/*
 * DiTAA - Diagrams Through Ascii Art
 * 
 * Copyright (C) 2004 Efstathios Sideris
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *   
 */
package org.stathissideris.ascii2image.graphics;

import java.awt.Color;
import java.awt.Font;
import java.util.*;

import org.apache.batik.gvt.CompositeGraphicsNode;
import org.stathissideris.ascii2image.core.ConversionOptions;
import org.stathissideris.ascii2image.core.Pair;
import org.stathissideris.ascii2image.core.ProcessingOptions;
import org.stathissideris.ascii2image.text.*;

/**
 * @ invariant: Every DiagramComponent in shapes, compositeShapes and textObjects has x-bounds between 0 and this.width
 * 				Every DiagramComponent in shapes, compositeShapes and textObjects has y-bounds between 0 and this.height
 *
 * @author Efstathios Sideris
 */
public class Diagram {

	private static final boolean DEBUG = false;
	private static final boolean VERBOSE_DEBUG = false;

	private ArrayList shapes = new ArrayList();
	private ArrayList compositeShapes = new ArrayList();
	private ArrayList textObjects = new ArrayList();
	
	private int pxWidth, pxHeight;
	private int cellWidth, cellHeight;
	
	
	/**
	 * 
	 * <p>An outline of the inner workings of this very important (and monstrous)
	 * constructor is presented here. Boundary processing is the first step
	 * of the process:</p>
	 * 
	 * <ol>
	 *   <li>Copy the grid into a work grid and remove all type-on-line
	 *       and point markers from the work grid</li>
	 *   <li>Split grid into distinct shapes by plotting the grid
	 * 	     onto an AbstractionGrid and its getDistinctShapes() method.</li>
	 *   <li>Find all the possible boundary sets of each of the
	 *       distinct shapes. This can produce duplicate shapes (if the boundaries
	 *       are the same when filling from the inside and the outside).</li>
	 *   <li>Remove duplicate boundaries.</li>
	 *   <li>Remove obsolete boundaries. Obsolete boundaries are the ones that are
	 *       the sum of their parts when plotted as filled shapes. (see method
	 *       removeObsoleteShapes())</li>
	 *   <li>Seperate the found boundary sets to open, closed or mixed
	 *       (See CellSet class on how its done).</li>
	 *   <li>Are there any closed boundaries?
	 *        <ul>
	 *           <li>YES. Subtract all the closed boundaries from each of the
	 *           open ones. That should convert the mixed shapes into open.</li>
	 *           <li>NO. In this (harder) case, we use the method
	 *           breakTrulyMixedBoundaries() of CellSet to break boundaries
	 *           into open and closed shapes (would work in any case, but it's
	 *           probably slower than the other method). This method is based
	 *           on tracing from the lines' ends and splitting when we get to
	 *           an intersection.</li>
	 *        </ul>
	 *   </li>
	 *   <li>If we had to eliminate any mixed shapes, we seperate the found
	 *   boundary sets again to open, closed or mixed.</li>
	 * </ol>
	 * 
	 * <p>At this stage, the boundary processing is all complete and we
	 * proceed with using those boundaries to create the shapes:</p>
	 * 
	 * <ol>
	 *   <li>Create closed shapes.</li>
	 *   <li>Create open shapes. That's when the line end corrections are
	 *   also applied, concerning the positioning of the ends of lines
	 *   see methods connectEndsToAnchors() and moveEndsToCellEdges() of
	 *   DiagramShape.</li>
	 *   <li>Assign color codes to closed shapes.</li>
	 *   <li>Assing extended markup tags to closed shapes.</p>
	 *   <li>Create arrowheads.</p>
	 *   <li>Create point markers.</p>
	 * </ol>
	 * 
	 * <p>Finally, the text processing occurs: [pending]</p>
	 * 
	 * @param grid
	 * @param options
	 * @requires all arguments are non-null
	 * @ensures this.shapes, this.compositeShapes and this.textObjects represent every entity in the ASCII art
	 * represented by grid. WARNING: It is likely that remaining bugs violate this condition in some edge cases!
	 */
	public Diagram(TextGrid grid, ConversionOptions options) {
		assert (grid != null && options != null);

		this.cellWidth = options.renderingOptions.getCellWidth();
		this.cellHeight = options.renderingOptions.getCellHeight();
		
		pxWidth = grid.getWidth() * cellWidth;
		pxHeight = grid.getHeight() * cellHeight;
		
		TextGrid workGrid = new TextGrid(grid);
		workGrid.replaceTypeOnLine();
		workGrid.replacePointMarkersOnLine();
		if(DEBUG) workGrid.printDebug();
		
		int width = grid.getWidth();
		int height = grid.getHeight();

		//split distinct shapes using AbstractionGrid 
		AbstractionGrid temp = new AbstractionGrid(workGrid, workGrid.getAllBoundaries());
		ArrayList connectedComponents = temp.getDistinctShapes();
		
		if(DEBUG){
			System.out.println("******* Distinct shapes found using AbstractionGrid *******");
			Iterator dit = connectedComponents.iterator();
			while (dit.hasNext()) {
				CellSet set = (CellSet) dit.next();
				set.printAsGrid();
			}
			System.out.println("******* Same set of shapes after processing them by filling *******");
		}

		//Find all the boundaries by using the special version of the filling method
		//(fills in a different buffer than the buffer it reads from)
		ArrayList boundarySetsStep2 = findInteriorExteriorBoundaries(connectedComponents, workGrid);

		//This seems to be a bug below...we are calling removeDuplicateSets twice? ~ CHRIS MENART
		if (DEBUG)
			System.out.println("******* Removed duplicates *******");

		boundarySetsStep2 = CellSet.removeDuplicateSets(boundarySetsStep2);

		if(DEBUG){
			Iterator dit = boundarySetsStep2.iterator();
			while (dit.hasNext()) {
				CellSet set = (CellSet) dit.next();
				set.printAsGrid();
			}
		}

		int originalSize = boundarySetsStep2.size();
		boundarySetsStep2 = CellSet.removeDuplicateSets(boundarySetsStep2);
		if(DEBUG) {
			System.out.println(
				"******* Removed duplicates: there were "
				+originalSize
				+" shapes and now there are "
				+boundarySetsStep2.size());
		} 

		Pair<ArrayList<CellSet>, ArrayList<CellSet>> openClosed = breakBoundariesIntoOpenAndClosed(boundarySetsStep2, workGrid);
		ArrayList<CellSet> open = openClosed.first;
		ArrayList<CellSet> closed = openClosed.second;

		boolean removedAnyObsolete = removeObsoleteShapes(workGrid, closed);
		
		boolean allCornersRound = false;
		if(options.processingOptions.areAllCornersRound()) allCornersRound = true;
		
		//make shapes from the boundary sets
		//make closed shapes
		ArrayList closedShapes = new ArrayList();
		Iterator sets = closed.iterator();
		while(sets.hasNext()){
			CellSet set = (CellSet) sets.next();
			DiagramComponent shape = DiagramComponent.createClosedFromBoundaryCells(workGrid, set, cellWidth, cellHeight, allCornersRound); 
			if(shape != null){
				if(shape instanceof DiagramShape){
					addToShapes((DiagramShape) shape);
					closedShapes.add(shape);
				} else if(shape instanceof CompositeDiagramShape)
					addToCompositeShapes((CompositeDiagramShape) shape);
			}
		}

		if(options.processingOptions.performSeparationOfCommonEdges())
			separateCommonEdges(closedShapes);

		//make open shapes
		sets = open.iterator();
		while(sets.hasNext()){
			CellSet set = (CellSet) sets.next();
			if(set.size() == 1){ //single cell "shape"
				TextGrid.Cell cell = (TextGrid.Cell) set.getFirst();
				if(!grid.cellContainsDashedLineChar(cell)) { 
					DiagramShape shape = DiagramShape.createSmallLine(workGrid, cell, cellWidth, cellHeight); 
					if(shape != null) {
						addToShapes(shape); 
						shape.connectEndsToAnchors(workGrid, this);
					}
				}
			} else { //normal shape
				DiagramComponent shape =
					CompositeDiagramShape
						.createOpenFromBoundaryCells(
								workGrid, set, cellWidth, cellHeight, allCornersRound);

				if(shape != null){
					if(shape instanceof CompositeDiagramShape){
						addToCompositeShapes((CompositeDiagramShape) shape);
						((CompositeDiagramShape) shape).connectEndsToAnchors(workGrid, this);
					} else if(shape instanceof DiagramShape) {
						addToShapes((DiagramShape) shape);
						((DiagramShape) shape).connectEndsToAnchors(workGrid, this);
						((DiagramShape) shape).moveEndsToCellEdges(grid, this);
					}
				}
					
			}
		}

		assignColorCodes(grid);

		assignMarkup(grid, options.processingOptions);
		
		makeArrowheads(workGrid);

		makePointMarkers(grid);

		removeDuplicateShapes();
		
		if(DEBUG) System.out.println("Shape count: "+shapes.size());
		if(DEBUG) System.out.println("Composite shape count: "+compositeShapes.size());

		extractText(grid);

		if (DEBUG)
			System.out.println("Corrected color of text according to underlying color");

	}
	
	/**
	 * Returns a list of all DiagramShapes in the Diagram, including
	 * the ones within CompositeDiagramShapes
	 * 
	 * @requires true
	 * @ensures \result is an ArrayList containing all elements of this.shapes and all elements of this.compositeShapes
	 */
	public /*@pure@*/ ArrayList<DiagramShape> getAllDiagramShapes(){
		ArrayList shapes = new ArrayList();
		shapes.addAll(this.getShapes());
		
		Iterator shapesIt = this.getCompositeShapes().iterator();
		while(shapesIt.hasNext()){
			CompositeDiagramShape compShape = (CompositeDiagramShape) shapesIt.next();
			shapes.addAll(compShape.getShapes());
		}

		for (Object e: this.getShapes()) {
			assert (shapes.contains(e));
		}
		for (Object e: this.getCompositeShapes()) {
			for (Object o: ((CompositeDiagramShape)e).getShapes()) {
				assert (shapes.contains(o));
			}
		}

		return shapes;
	}
	
	/**
	 * Removes the sets from <code>sets</code>that are the sum of their parts
	 * when plotted as filled shapes.
	 *
	 * @requires all arguments are non-null. All CellSets in sets are within the x and y-bounds of grid
	 * @assignable sets
	 * @ensures A minimal number of CellSets are removed from sets s.t. no CellSet 'A' in sets covers an area which is
	 * exactly the same as the  union of areas covered by any subset of CellSets in sets excluding A.
	 * @return true if it removed any shapes from sets.
	 * 
	 */
	private boolean removeObsoleteShapes(TextGrid grid, ArrayList<CellSet> sets){
		assert (grid != null && sets != null);
		for (CellSet s: sets) {
			assert (s.getMinX() >= 0 && s.getMaxX() <= grid.getWidth() &&
						s.getMinY() >= 0 && s.getMaxY() <= grid.getHeight());
		}
		int initSize = sets.size();

		if (DEBUG)
			System.out.println("******* Removing obsolete shapes *******");
		
		boolean removedAny = false;
		
		ArrayList<CellSet> filledSets = new ArrayList<CellSet>();

		Iterator it;

		if(VERBOSE_DEBUG) {
			System.out.println("******* Sets before *******");
			it = sets.iterator();
			while(it.hasNext()){
				CellSet set = (CellSet) it.next();
				set.printAsGrid();
			}
		}

		//make filled versions of all the boundary sets
		it = sets.iterator();
		while(it.hasNext()){
			CellSet set = (CellSet) it.next();
			set = set.getFilledEquivalent(grid);
			if(set == null){
				return false;
			} else filledSets.add(set);
		}
		
		ArrayList<Integer> toBeRemovedIndices = new ArrayList<Integer>();
		it = filledSets.iterator();
		while(it.hasNext()){
			CellSet set = (CellSet) it.next();
			
			if(VERBOSE_DEBUG){
				System.out.println("*** Deciding if the following should be removed:");
				set.printAsGrid();
			}
			
			//find the other sets that have common cells with set
			ArrayList<CellSet> common = new ArrayList<CellSet>();
			common.add(set);
			Iterator it2 = filledSets.iterator();
			while(it2.hasNext()){
				CellSet set2 = (CellSet) it2.next();
				if(set != set2 && set.hasCommonCells(set2)){
					common.add(set2);
				}
			}
			//it only makes sense for more than 2 sets
			if(common.size() == 2) continue;
			
			//find largest set
			CellSet largest = set;
			it2 = common.iterator();
			while(it2.hasNext()){
				CellSet set2 = (CellSet) it2.next();
				if(set2.size() > largest.size()){
					largest = set2;
				}
			}
			
			if(VERBOSE_DEBUG){
				System.out.println("Largest:");
				largest.printAsGrid();
			}

			//see if largest is sum of others
			common.remove(largest);

			//make the sum set of the small sets on a grid
			TextGrid gridOfSmalls = new TextGrid(largest.getMaxX() + 2, largest.getMaxY() + 2);
			CellSet sumOfSmall = new CellSet();
			it2 = common.iterator();
			while(it2.hasNext()){
				CellSet set2 = (CellSet) it2.next();
				if(VERBOSE_DEBUG){
					System.out.println("One of smalls:");
					set2.printAsGrid();
				}
				gridOfSmalls.fillCellsWith(set2, '*');
			}
			if(VERBOSE_DEBUG){
				System.out.println("Sum of smalls:");
				gridOfSmalls.printDebug();
			}
			TextGrid gridLargest = new TextGrid(largest.getMaxX() + 2, largest.getMaxY() + 2);
			gridLargest.fillCellsWith(largest, '*');

			int index = filledSets.indexOf(largest);
			if(gridLargest.equals(gridOfSmalls)
					&& !toBeRemovedIndices.contains(new Integer(index))) {
				toBeRemovedIndices.add(new Integer(index));
				if (DEBUG){
					System.out.println("Decided to remove set:");
					largest.printAsGrid();
				}
			} else if (DEBUG){
				System.out.println("This set WILL NOT be removed:");
				largest.printAsGrid();
			}
			//if(gridLargest.equals(gridOfSmalls)) toBeRemovedIndices.add(new Integer(index));
		}
		
		ArrayList<CellSet> setsToBeRemoved = new ArrayList<CellSet>();
		it = toBeRemovedIndices.iterator();
		while(it.hasNext()){
			int i = ((Integer) it.next()).intValue();
			setsToBeRemoved.add(sets.get(i));
		}
	
		it = setsToBeRemoved.iterator();
		while(it.hasNext()){
			CellSet set = (CellSet) it.next();
			removedAny = true;
			sets.remove(set);
		}
	
		if(VERBOSE_DEBUG) {
			System.out.println("******* Sets after *******");
			it = sets.iterator();
			while(it.hasNext()){
				CellSet set = (CellSet) it.next();
				set.printAsGrid();
			}
		}

		assert((removedAny && sets.size() < initSize) || (!removedAny && sets.size() == initSize));

		return removedAny;
	}
	
	public /*@pure*/ float getMinimumOfCellDimension(){
		return Math.min(getCellWidth(), getCellHeight());
	}

	/*
	 * Pulls apart (in pixel space) any edges of DiagramComponents in shapes that overlap.
	 * @requires shapes is not null.
	 * @ensures For each pair of edges in shapes which overlap (cover any of the exact same pixels), the endpoints of
	 * both edges are shifted by getMinimumOfCellDimension()/5, in a direction such that the orientation of each edge is
	 * unchanged and the area of each parent shape decreases rather than increases.
	 */
	private void separateCommonEdges(ArrayList shapes){
		assert (shapes != null);

		float offset = getMinimumOfCellDimension() / 5;

		ArrayList<ShapeEdge> edges = new ArrayList<ShapeEdge>();

		//get all adges
		Iterator it = shapes.iterator();
		while (it.hasNext()) {
			DiagramShape shape = (DiagramShape) it.next();
			edges.addAll(shape.getEdges());
		}
		
		//group edges into pairs of touching edges
		ArrayList<Pair<ShapeEdge, ShapeEdge>> listOfPairs = new ArrayList<Pair<ShapeEdge, ShapeEdge>>();
		it = edges.iterator();
		
		//all-against-all touching test for the edges
		int startIndex = 1; //skip some to avoid duplicate comparisons and self-to-self comparisons
		
		while(it.hasNext()){
			ShapeEdge edge1 = (ShapeEdge) it.next();
			
			for(int k = startIndex; k < edges.size(); k++) {
				ShapeEdge edge2 =  edges.get(k);
				
				if(edge1.touchesWith(edge2)) {
					listOfPairs.add(new Pair<ShapeEdge, ShapeEdge>(edge1, edge2));
				}
			}
			startIndex++;
		}
		
		ArrayList<ShapeEdge> movedEdges = new ArrayList<ShapeEdge>();
		
		//move equivalent edges inwards
		it = listOfPairs.iterator();
		while(it.hasNext()){
			Pair<ShapeEdge, ShapeEdge> pair = (Pair<ShapeEdge, ShapeEdge>) it.next();
			if(!movedEdges.contains(pair.first)) {
				pair.first.moveInwardsBy(offset);
				movedEdges.add(pair.first);
			}
			if(!movedEdges.contains(pair.second)) {
				pair.second.moveInwardsBy(offset);
				movedEdges.add(pair.second);
			}
		}

	}
	
	/*
	 * @requires this.shapes is non-null
	 * @ensures Removes a minimal number of DiagramComponents from this.shapes s.t. there do NOT exist any two integers
	 * i and j where i != j but this.shapes[i].equals(this.shapes[j])
	 */
	//TODO: removes more than it should
	private void removeDuplicateShapes() {
		assert (this.shapes != null);
		int initSize = this.shapes.size();

		ArrayList originalShapes = new ArrayList();

		Iterator shapesIt = getShapesIterator();
		while(shapesIt.hasNext()){
			DiagramShape shape = (DiagramShape) shapesIt.next();
			boolean isOriginal = true;
			Iterator originals = originalShapes.iterator();
			while(originals.hasNext()){
				DiagramShape originalShape = (DiagramShape) originals.next();
				if(shape.equals(originalShape)){
					isOriginal = false;
				}
			}
			if(isOriginal) originalShapes.add(shape);
		}

		shapes.clear();
		shapes.addAll(originalShapes);

		assert (shapes.size() <= initSize);
		for (int i = 0; i < shapes.size(); i++) {
			for (int j = 0; j < i; j++) {
				assert (!shapes.get(i).equals(shapes.get(j)));
			}
		}
	}

	/*
	 * Decomposes the unclassifiedBoundaries into a set of Open and Closed boundaries which are easily rendered.
	 * @ assignable unclassifiedBoundaries
	 * @ requires grid does not contain text or point markers 'over' what are intended to be contiguous lines.
	 * @ ensures let open = result[0] and closed = result[1]:
	 * @ Each CellSet in open is an open line &&
	 * @ Each CellSet in closed is a simple closed curve &&
	 * @ The union of all CellSets in open and closed covered the union of cells in \old(unclassifiedBoundaries).
	 */
	private Pair<ArrayList<CellSet>, ArrayList<CellSet>> breakBoundariesIntoOpenAndClosed(
			ArrayList<CellSet> unclassifiedBoundaries, TextGrid grid) {

		//for assertion
		CellSet oldUnion = new CellSet();
		for (CellSet c: unclassifiedBoundaries) {
			oldUnion.addSet(c);
		}

		//split boundaries to open, closed and mixed
		ArrayList open;
		ArrayList closed;
		ArrayList mixed;
		Iterator sets;
		do {
			if (DEBUG)
				System.out.println("******* Evaluating openess *******");

			//Evaluate open-ness of each boundary.
			open = new ArrayList();
			closed = new ArrayList();
			mixed = new ArrayList();

			sets = unclassifiedBoundaries.iterator();
			while(sets.hasNext()){
				CellSet set = (CellSet) sets.next();
				int type = set.getType(grid);
				if(type == CellSet.TYPE_CLOSED) closed.add(set);
				else if(type == CellSet.TYPE_OPEN) open.add(set);
				else if(type == CellSet.TYPE_MIXED) mixed.add(set);
				if(DEBUG){
					if(type == CellSet.TYPE_CLOSED) System.out.println("Closed boundaries:");
					else if(type == CellSet.TYPE_OPEN) System.out.println("Open boundaries:");
					else if(type == CellSet.TYPE_MIXED) System.out.println("Mixed boundaries:");
					set.printAsGrid();
				}
			}

			/*
			If there are mixed shapes, decompose them into open and closed shapes. This could, in edge cases, take
			multiple passes, although not usually. ~Chris M.
			 */

			if(mixed.size() > 0 && closed.size() > 0) {
				// some mixed shapes must be broken down by
				// subtracting the closed shapes we have previously ID'd from them
				if (DEBUG)
					System.out.println("******* Eliminating mixed shapes (subtracting known closed curves) *******");

				//subtract from each of the mixed sets all the closed sets
				sets = mixed.iterator();
				while(sets.hasNext()){
					CellSet set = (CellSet) sets.next();
					Iterator closedSets = closed.iterator();
					while(closedSets.hasNext()){
						CellSet closedSet = (CellSet) closedSets.next();
						if (closedSet.isSubsetOf(set)) {
							set.subtractSet(closedSet);
						}
					}
					// this is necessary because some mixed sets produce
					// several distinct open sets after you subtract the
					// closed sets from them
					if(set.getType(grid) == CellSet.TYPE_OPEN) {
						unclassifiedBoundaries.remove(set);
						unclassifiedBoundaries.addAll(set.breakIntoDistinctBoundaries(grid));
					}
				}
			}
			if(mixed.size() > 0) {
				// Now we will have to
				// handle mixed shape on its own
				// an example of this case is the following:
				// +-----+
				// |  A  |C                 B
				// +  ---+-------------------
				// |     |
				// +-----+
				// This algorithm works by tracing in from dead-ends and pulling out open shapes.

				if (DEBUG)
					System.out.println("******* Eliminating mixed shapes (advanced tracing algorithm for very mixed shapes) *******");

				sets = mixed.iterator();
				while(sets.hasNext()){
					CellSet set = (CellSet) sets.next();
					unclassifiedBoundaries.remove(set);
					unclassifiedBoundaries.addAll(set.breakTrulyMixedBoundaries(grid));
				}

			} else {
				if (DEBUG)
					System.out.println("No mixed shapes found. Skipped mixed shape elimination step");
			}

		} while (mixed.size() > 0);

		//long assertions
		for (CellSet c: (ArrayList<CellSet>)open) {
			assert (c.getType(grid) == CellSet.TYPE_OPEN);
		}
		for (CellSet c: (ArrayList<CellSet>)closed) {
			assert (c.getType(grid) == CellSet.TYPE_CLOSED);
		}
		CellSet newUnion = new CellSet();
		for (CellSet c: (ArrayList<CellSet>)open) {
			newUnion.addSet(c);
		}
		for (CellSet c: (ArrayList<CellSet>)closed) {
			newUnion.addSet(c);
		}
		assert(newUnion.equals(oldUnion));

		return new Pair<ArrayList<CellSet>, ArrayList<CellSet>>(open, closed);
	}

	/*
	 * Takes a list of connected components and further breaks them apart into 'boundaries' by finding sets of cells
	 * That form interior or exterior boundaries to regions of blank space. Uses an AbstractionGrid for this, so that it
	 * can process the 'empty space' between directly-adjacent characters.
	 * @ requires grid does not contain text or point markers 'over' what are intended to be contiguous lines. &&
	 * 		topologically, each CellSet in connectedComponents contains no 1-dimensional holes (i.e. each are contiguous)
	 * @ ensures the union of all CellSets in \result matches the union off all CellSets in connectedComponents &&
	 * @ 	topologically, each CellSet in \result contains 0 or 1 2-dimensional holes.
	 */
	private ArrayList<CellSet> findInteriorExteriorBoundaries(ArrayList<CellSet> connectedComponents, TextGrid grid) {

		//Find all the boundaries by using the special version of the filling method
		//(fills in a different buffer than the buffer it reads from)
		ArrayList boundarySetsStep2 = new ArrayList();
		Iterator boundarySetIt = connectedComponents.iterator();
		while (boundarySetIt.hasNext()) {
			CellSet set = (CellSet) boundarySetIt.next();

			int gridHeight = grid.getHeight();
			int gridWidth = grid.getWidth();

			//the fill buffer keeps track of which cells have been
			//filled already
			TextGrid fillBuffer = new TextGrid(gridWidth * 3, gridHeight * 3);

			for (int yi = 0; yi < gridHeight * 3; yi++) {
				for (int xi = 0; xi < gridWidth * 3; xi++) {
					if (fillBuffer.isBlank(xi, yi)) {

						TextGrid copyGrid = new AbstractionGrid(grid, set).getCopyOfInternalBuffer();

						if (VERBOSE_DEBUG) {
							System.out.println("Found empty cell " + xi + ", " + yi);
							copyGrid.printDebug();
						}

						CellSet boundaries =
								copyGrid
										.findBoundariesExpandingFrom(copyGrid.new Cell(xi, yi));
						if (boundaries.size() == 0) {
							if (DEBUG) {
								System.out.println("findBoundariesExpandingFrom found empty boundary list!");
							}
							continue; //i'm not sure why these occur
						}
						boundarySetsStep2.add(boundaries.makeScaledOneThirdEquivalent());

						copyGrid = new AbstractionGrid(grid, set).getCopyOfInternalBuffer();
						CellSet filled =
								copyGrid
										.fillContinuousArea(copyGrid.new Cell(xi, yi), '*');
						fillBuffer.fillCellsWith(filled, '*');
						fillBuffer.fillCellsWith(boundaries, '-');

						if (DEBUG) {
							//System.out.println("Fill buffer:");
							//fillBuffer.printDebug();
							boundaries.makeScaledOneThirdEquivalent().printAsGrid();
							System.out.println("-----------------------------------");
						}

					}
				}
			}
		}
		return boundarySetsStep2;
	}

	/*
	 * Find color codes in grid and assign them to our shapes
	 * @ requires this.shapes is not null
	 * @ ensures \foreach shape in shapes, shape.fillColor is set to a color code inside the shape as represented by
	 * @ 	grid, if any such color codes exist.
	 */
	private void assignColorCodes(TextGrid grid) {
		assert (this.shapes != null);

		//TODO: text on line should not change its color
		//TODO: each color tag should be assigned to the smallest containing shape (like shape tags)

		Iterator cellColorPairs = grid.findColorCodes().iterator();
		while(cellColorPairs.hasNext()){
			TextGrid.CellColorPair pair =
					(TextGrid.CellColorPair) cellColorPairs.next();
			ShapePoint point =
					new ShapePoint(getCellMidX(pair.cell), getCellMidY(pair.cell));
			Iterator shapes = getShapes().iterator();
			while(shapes.hasNext()){
				DiagramShape shape = (DiagramShape) shapes.next();
				if(shape.contains(point)) shape.setFillColor(pair.color);
			}
		}
	}

	/*
	 * Applies markup tags from grid to shapes in this.shapes containing them.
	 * @ requires: Arguments are non-null && this.shapes is non-null
	 * @ ensures: \foreach shape in shapes, if any markup tag in grid falls within shape's bounds, shape's type and
	 * 		definition are set according to one such markup tag.
	 */
	private void assignMarkup(TextGrid grid, ProcessingOptions processingOptions) {
		assert (grid != null && processingOptions != null && this.shapes != null);

		//assign markup to shapes
		Iterator cellTagPairs = grid.findMarkupTags().iterator();
		while(cellTagPairs.hasNext()){
			TextGrid.CellTagPair pair =
					(TextGrid.CellTagPair) cellTagPairs.next();
			ShapePoint point =
					new ShapePoint(getCellMidX(pair.cell), getCellMidY(pair.cell));

			//find the smallest shape that contains the tag
			DiagramShape containingShape = null;
			Iterator shapes = getShapes().iterator();
			while(shapes.hasNext()) {
				DiagramShape shape = (DiagramShape) shapes.next();
				if(shape.contains(point)){
					if(containingShape == null){
						containingShape = shape;
					} else {
						if(shape.isSmallerThan(containingShape)){
							containingShape = shape;
						}
					}
				}
			}

			//this tag is not within a shape, skip
			if(containingShape == null) continue;

			//TODO: the code below could be a lot more concise
			if(pair.tag.equals("d")){
				CustomShapeDefinition def =
						processingOptions.getFromCustomShapes("d");
				if(def == null)
					containingShape.setType(DiagramShape.TYPE_DOCUMENT);
				else {
					containingShape.setType(DiagramShape.TYPE_CUSTOM);
					containingShape.setDefinition(def);
				}
			} else if(pair.tag.equals("s")){
				CustomShapeDefinition def =
						processingOptions.getFromCustomShapes("s");
				if(def == null)
					containingShape.setType(DiagramShape.TYPE_STORAGE);
				else {
					containingShape.setType(DiagramShape.TYPE_CUSTOM);
					containingShape.setDefinition(def);
				}
			} else if(pair.tag.equals("io")){
				CustomShapeDefinition def =
						processingOptions.getFromCustomShapes("io");
				if(def == null)
					containingShape.setType(DiagramShape.TYPE_IO);
				else {
					containingShape.setType(DiagramShape.TYPE_CUSTOM);
					containingShape.setDefinition(def);
				}
			} else if(pair.tag.equals("c")){
				CustomShapeDefinition def =
						processingOptions.getFromCustomShapes("c");
				if(def == null)
					containingShape.setType(DiagramShape.TYPE_DECISION);
				else {
					containingShape.setType(DiagramShape.TYPE_CUSTOM);
					containingShape.setDefinition(def);
				}
			} else if(pair.tag.equals("mo")){
				CustomShapeDefinition def =
						processingOptions.getFromCustomShapes("mo");
				if(def == null)
					containingShape.setType(DiagramShape.TYPE_MANUAL_OPERATION);
				else {
					containingShape.setType(DiagramShape.TYPE_CUSTOM);
					containingShape.setDefinition(def);
				}
			} else if(pair.tag.equals("tr")){
				CustomShapeDefinition def =
						processingOptions.getFromCustomShapes("tr");
				if(def == null)
					containingShape.setType(DiagramShape.TYPE_TRAPEZOID);
				else {
					containingShape.setType(DiagramShape.TYPE_CUSTOM);
					containingShape.setDefinition(def);
				}
			} else if(pair.tag.equals("o")){
				CustomShapeDefinition def =
						processingOptions.getFromCustomShapes("o");
				if(def == null)
					containingShape.setType(DiagramShape.TYPE_ELLIPSE);
				else {
					containingShape.setType(DiagramShape.TYPE_CUSTOM);
					containingShape.setDefinition(def);
				}
			} else {
				CustomShapeDefinition def =
						processingOptions.getFromCustomShapes(pair.tag);
				containingShape.setType(DiagramShape.TYPE_CUSTOM);
				containingShape.setDefinition(def);
			}
		}
	}

	/*
	 * Pulls all free text out of grid, converts into DiagramTexts, and adds to textObjects
	 * @ assignable this.textObjects
	 * @ requires this.textObjects is non null && this.shapes is non-null && grid is non-null
	 * @ ensures all 'free text' in grid (text not part of another DITAA entity) is represented by a DiagramText in
	 * 		this.textObjects &&
	 * @ 	any DiagramText overlapping any shapes in this.shapes is set to a color contrasting with one such shape. Any
	 * @	DiagramText overlapping no colored shapes is colored black.
	 * @ 	any DiagramText overlapping any shapes of type TYPE_CUSTOM in this.shapes is outlined.
	 */
	private void extractText(TextGrid grid) {
		assert (this.textObjects != null && this.shapes != null && grid != null);

		//copy so we don't modify original
		TextGrid workGrid = new TextGrid(grid);
		workGrid.removeNonText();

		// ****** handle text *******
		//break up text into groups
		TextGrid textGroupGrid = new TextGrid(workGrid);
		CellSet gaps = textGroupGrid.getAllBlanksBetweenCharacters();
		//kludge
		textGroupGrid.fillCellsWith(gaps, '|');
		CellSet nonBlank = textGroupGrid.getAllNonBlank();
		ArrayList textGroups = nonBlank.breakIntoDistinctBoundaries();
		if(DEBUG) System.out.println(textGroups.size()+" text groups found");

		Font font = FontMeasurer.instance().getFontFor(cellHeight);

		Iterator textGroupIt = textGroups.iterator();
		while(textGroupIt.hasNext()){
			CellSet textGroupCellSet = (CellSet) textGroupIt.next();

			TextGrid isolationGrid = new TextGrid(pxWidth, pxHeight);
			workGrid.copyCellsTo(textGroupCellSet, isolationGrid);

			ArrayList strings = isolationGrid.findStrings();
			Iterator it = strings.iterator();
			while(it.hasNext()){
				TextGrid.CellStringPair pair = (TextGrid.CellStringPair) it.next();
				TextGrid.Cell cell = pair.cell;
				String string = pair.string;
				if (DEBUG)
					System.out.println("Found string "+string);
				TextGrid.Cell lastCell = isolationGrid.new Cell(cell.x + string.length() - 1, cell.y);

				int minX = getCellMinX(cell);
				int y = getCellMaxY(cell);
				int maxX = getCellMaxX(lastCell);

				DiagramText textObject;
				if(FontMeasurer.instance().getWidthFor(string, font) > maxX - minX){ //does not fit horizontally
					Font lessWideFont = FontMeasurer.instance().getFontFor(maxX - minX, string);
					textObject = new DiagramText(minX, y, string, lessWideFont);
				} else textObject = new DiagramText(minX, y, string, font);

				textObject.centerVerticallyBetween(getCellMinY(cell), getCellMaxY(cell));

				//TODO: if the strings start with bullets they should be aligned to the left

				//position text correctly
				int otherStart = isolationGrid.otherStringsStartInTheSameColumn(cell);
				int otherEnd = isolationGrid.otherStringsEndInTheSameColumn(lastCell);
				if(0 == otherStart && 0 == otherEnd) {
					textObject.centerHorizontallyBetween(minX, maxX);
				} else if(otherEnd > 0 && otherStart == 0) {
					textObject.alignRightEdgeTo(maxX);
				} else if(otherEnd > 0 && otherStart > 0){
					if(otherEnd > otherStart){
						textObject.alignRightEdgeTo(maxX);
					} else if(otherEnd == otherStart){
						textObject.centerHorizontallyBetween(minX, maxX);
					}
				}

				addToTextObjects(textObject);
			}
		}

		if (DEBUG)
			System.out.println("Positioned text");

		//correct the color of the text objects according
		//to the underlying color
		Iterator shapes = this.getAllDiagramShapes().iterator();
		while(shapes.hasNext()){
			DiagramShape shape = (DiagramShape) shapes.next();
			Color fillColor = shape.getFillColor();
			if(shape.isClosed()
					&& shape.getType() != DiagramShape.TYPE_ARROWHEAD
					&& fillColor != null
					&& BitmapRenderer.isColorDark(fillColor)){
				Iterator textObjects = getTextObjects().iterator();
				while(textObjects.hasNext()){
					DiagramText textObject = (DiagramText) textObjects.next();
					if(shape.intersects(textObject.getBounds())){
						textObject.setColor(Color.white);
					}
				}
			}
		}

		//set outline to true for test within custom shapes
		shapes = this.getAllDiagramShapes().iterator();
		while(shapes.hasNext()){
			DiagramShape shape = (DiagramShape) shapes.next();
			if(shape.getType() == DiagramShape.TYPE_CUSTOM){
				Iterator textObjects = getTextObjects().iterator();
				while(textObjects.hasNext()){
					DiagramText textObject = (DiagramText) textObjects.next();
					if (shape.intersects(textObject.getBounds())) {
						textObject.setHasOutline(true);
						textObject.setColor(DiagramText.DEFAULT_COLOR);
					}
				}
			}
		}
	}

	/*
	 * Finds the arrowheads in grid and adds them to this.shapes
	 * @ assignable this.shapes
	 * @ requires grid is non-null, this.shapes it non-null
	 * @ ensures all arrowhead characters in grid are represented by shapes in this.shapes
	 */
	private void makeArrowheads(TextGrid grid) {
		assert (grid != null && this.shapes != null);

		//make arrowheads
		Iterator arrowheadCells = grid.findArrowheads().iterator();
		while(arrowheadCells.hasNext()){
			TextGrid.Cell cell = (TextGrid.Cell) arrowheadCells.next();
			DiagramShape arrowhead = DiagramShape.createArrowhead(grid, cell, cellWidth, cellHeight);
			if(arrowhead != null) addToShapes(arrowhead);
			else System.err.println("Could not create arrowhead shape. Unexpected error.");
		}
	}

	/*
	 * Finds the point markers in grid and adds them to this.shapes
	 * @ assignable this.shapes
	 * @ requires grid is non-null, this.shapes is non-null
	 * @ ensures all point markers ('*') in grid are represented by shapes in this.shapes
	 */
	private void makePointMarkers(TextGrid grid) {
		assert (grid != null && this.shapes != null);

		//make point markers
		Iterator markersIt = grid.getPointMarkersOnLine().iterator();
		while (markersIt.hasNext()) {
			TextGrid.Cell cell = (TextGrid.Cell) markersIt.next();

			DiagramShape mark = new DiagramShape();
			mark.addToPoints(new ShapePoint(
					getCellMidX(cell),
					getCellMidY(cell)
			));
			mark.setType(DiagramShape.TYPE_POINT_MARKER);
			mark.setFillColor(Color.white);
			shapes.add(mark);
		}
	}

	private void addToTextObjects(DiagramText shape){
		textObjects.add(shape);
	}

	private void addToCompositeShapes(CompositeDiagramShape shape){
		compositeShapes.add(shape);
	}

	
	private void addToShapes(DiagramShape shape){
		shapes.add(shape);
	}
	
	public /*@pure@*/ Iterator getShapesIterator(){
		return shapes.iterator();
	}	

	/**
	 * @return
	 */
	public /*@pure@*/ int getPxHeight() {
		return pxHeight;
	}

	/**
	 * @return
	 */
	public /*@pure@*/ int getPxWidth() {
		return pxWidth;
	}

	/**
	 * @return
	 */
	public /*@pure@*/ int getCellWidth() {
		return cellWidth;
	}

	/**
	 * @return
	 */
	public /*@pure@*/ int getCellHeight() {
		return cellHeight;
	}

	/**
	 * @return
	 */
	public /*@pure@*/ ArrayList getCompositeShapes() {
		return compositeShapes;
	}

	/**
	 * @return
	 */
	public /*@pure@*/ ArrayList getShapes() {
		return shapes;
	}
	
	public /*@pure@*/ int getCellMinX(TextGrid.Cell cell){
		return getCellMinX(cell, cellWidth);
	}
	public /*@pure@*/ static int getCellMinX(TextGrid.Cell cell, int cellXSize){
		return cell.x * cellXSize;
	}

	public /*@pure@*/ int getCellMidX(TextGrid.Cell cell){
		return getCellMidX(cell, cellWidth);
	}
	public /*@pure@*/ static int getCellMidX(TextGrid.Cell cell, int cellXSize){
		return cell.x * cellXSize + cellXSize / 2;
	}

	public /*@pure@*/ int getCellMaxX(TextGrid.Cell cell){
		return getCellMaxX(cell, cellWidth);
	}
	public /*@pure@*/ static int getCellMaxX(TextGrid.Cell cell, int cellXSize){
		return cell.x * cellXSize + cellXSize;
	}

	public /*@pure@*/ int getCellMinY(TextGrid.Cell cell){
		return getCellMinY(cell, cellHeight);
	}
	public /*@pure@*/ static int getCellMinY(TextGrid.Cell cell, int cellYSize){
		return cell.y * cellYSize;
	}

	public /*@pure@*/ int getCellMidY(TextGrid.Cell cell){
		return getCellMidY(cell, cellHeight);
	}
	public /*@pure@*/ static int getCellMidY(TextGrid.Cell cell, int cellYSize){
		return cell.y * cellYSize + cellYSize / 2;
	}

	public /*@pure@*/ int getCellMaxY(TextGrid.Cell cell){
		return getCellMaxY(cell, cellHeight);
	}
	public /*@pure@*/ static int getCellMaxY(TextGrid.Cell cell, int cellYSize){
		return cell.y * cellYSize + cellYSize;
	}

	public /*@pure@*/ TextGrid.Cell getCellFor(ShapePoint point){
		if(point == null) throw new IllegalArgumentException("ShapePoint cannot be null");
		//TODO: the fake grid is a problem
		TextGrid g = new TextGrid();
		return g.new Cell((int) point.x / cellWidth,
							(int) point.y / cellHeight);
	}

	public /*@pure@*/ ArrayList getTextObjects() {
		return textObjects;
	}

}
