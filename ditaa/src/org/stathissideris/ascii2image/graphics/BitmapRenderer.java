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

import java.awt.BasicStroke;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;

import javax.imageio.ImageIO;

import org.stathissideris.ascii2image.core.ConversionOptions;
import org.stathissideris.ascii2image.core.RenderingOptions;
import org.stathissideris.ascii2image.core.Shape3DOrderingComparator;
import org.stathissideris.ascii2image.text.TextGrid;

/**
 * @ invariant: normalStroke and DashStroke are either null or hold Stroke objects which draw solid and dashed lines
 * respectively.
 * @author Efstathios Sideris
 */
public class BitmapRenderer {

	private static final boolean DEBUG = false;

	private static final String IDREGEX = "^.+_vfill$";
	
	Stroke normalStroke;
	Stroke dashStroke; 
	
	public static void main(String[] args) throws Exception {
		
		
		long startTime = System.currentTimeMillis();
		
		ConversionOptions options = new ConversionOptions();
		
		TextGrid grid = new TextGrid();
		
		String filename = "dak_orgstruktur_vs_be.ditaa.OutOfMemoryError.edit.txt";
		
		grid.loadFrom("tests/text/"+filename);
		
		Diagram diagram = new Diagram(grid, options);
		new BitmapRenderer().renderToPNG(diagram, "tests/images/"+filename+".png", options.renderingOptions);
		long endTime = System.currentTimeMillis();
		long totalTime  = (endTime - startTime) / 1000;
		System.out.println("Done in "+totalTime+"sec");
		
		File workDir = new File("tests/images");
		//Process p = Runtime.getRuntime().exec("display "+filename+".png", null, workDir);
	}

	/*
	 * @requires All arguments are non-null
	 * @ensures \result == True ==> writes renderToImage(diagram, options) to filename as a PNG.
	 */
	private boolean renderToPNG(Diagram diagram, String filename, RenderingOptions options){	
		assert (diagram != null && filename != null && options != null);

		RenderedImage image = renderToImage(diagram, options);
		
		try {
			File file = new File(filename);
			ImageIO.write(image, "png", file);
		} catch (IOException e) {
			//e.printStackTrace();
			System.err.println("Error: Cannot write to file "+filename);
			return false;
		}

		assert(new File(filename).exists());
		return true;
	}

	/*
	 * @requires all arguments are non-null
	 * @ensures Makes a BufferedImage image with the same size as Diagram and returns render(diagram, image, options)
	 */
	public RenderedImage renderToImage(Diagram diagram,  RenderingOptions options){
		assert (diagram != null && options != null);

		BufferedImage image = new BufferedImage(
					diagram.getPxWidth(),
					diagram.getPxHeight(),
					BufferedImage.TYPE_INT_RGB);

		assert(image.getWidth() == diagram.getPxWidth() && image.getHeight() == diagram.getPxHeight());

		return render(diagram, image, options);
	}

	/*
	 * One of the main functions in the program! Encodes what the render of a Diagram is.
	 * @requires all arguments are non-null
	 * @ensures draws a visual representation of diagram onto image. Overwrites any existing contents in image.
	 */
	public RenderedImage render(Diagram diagram, BufferedImage image,  RenderingOptions options){
		assert(diagram != null && image != null && options != null);

		Graphics2D g2 = image.createGraphics();
		g2.setColor(Color.white);
		//TODO: find out why the next line does not work
		g2.fillRect(0, 0, image.getWidth()+10, image.getHeight()+10);
		/*for(int y = 0; y < diagram.getHeight(); y ++)
			g2.drawLine(0, y, diagram.getWidth(), y);*/
		g2.dispose();

		if(options.dropShadows()) {
			image = dropShadows(diagram, image, options.performAntialias());
		}

		//destination = destination.getSubimage(blurRadius/2, blurRadius/2, image.getWidth(), image.getHeight());
		g2 = (Graphics2D) image.getGraphics();
		Object antialiasSetting = RenderingHints.VALUE_ANTIALIAS_OFF;
		if(options.performAntialias())
			antialiasSetting = RenderingHints.VALUE_ANTIALIAS_ON;
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialiasSetting);

		//fill and stroke
		float dashInterval = Math.min(diagram.getCellWidth(), diagram.getCellHeight()) / 2;
		//Stroke normalStroke = g2.getStroke();
		
		float strokeWeight = diagram.getMinimumOfCellDimension() / 10;
		
		normalStroke =
		  new BasicStroke(
			strokeWeight,
			//10,
			BasicStroke.CAP_ROUND,
			BasicStroke.JOIN_ROUND
		  );

		dashStroke = 
		  new BasicStroke(
			strokeWeight,
			BasicStroke.CAP_BUTT,
			BasicStroke.JOIN_ROUND,
			0,
			new float[] {dashInterval}, 
			0
		  );
		
		//TODO: at this stage we should draw the open shapes first in order to make sure they are at the bottom (this is useful for the {mo} shape) 

		ArrayList<DiagramShape> shapes = diagram.getAllDiagramShapes();
		if(DEBUG) System.out.println("Rendering "+shapes.size()+" shapes (groups flattened)");

		//find storage shapes
		ArrayList storageShapes = new ArrayList();
		Iterator shapesIt = shapes.iterator();
		while(shapesIt.hasNext()){
			DiagramShape shape = (DiagramShape) shapesIt.next();
			if(shape.getType() == DiagramShape.TYPE_STORAGE) {
				storageShapes.add(shape);
				continue;
			} 
		}

		//render storage shapes
		//special case since they are '3d' and should be
		//rendered bottom to top
		//TODO: known bug: if a storage object is within a bigger normal box, it will be overwritten in the main drawing loop
		//(BUT this is not possible since tags are applied to all shapes overlaping shapes)

		Collections.sort(storageShapes, new Shape3DOrderingComparator());
		
		g2.setStroke(normalStroke);
		shapesIt = storageShapes.iterator();
		while(shapesIt.hasNext()){
			DiagramShape shape = (DiagramShape) shapesIt.next();

			GeneralPath path;
			path = shape.makeIntoRenderPath(diagram);
			
			if(!shape.isStrokeDashed()) {
				if(shape.getFillColor() != null)
					g2.setColor(shape.getFillColor());
				else
					g2.setColor(Color.white);
				g2.fill(path);
			}

			if(shape.isStrokeDashed())
				g2.setStroke(dashStroke);
			else
				g2.setStroke(normalStroke);
			g2.setColor(shape.getStrokeColor());
			g2.draw(path);
		}

		//render the rest of the shapes
		ArrayList pointMarkers = new ArrayList();
		shapesIt = shapes.iterator();
		while(shapesIt.hasNext()){
			DiagramShape shape = (DiagramShape) shapesIt.next();
			if(shape.getType() == DiagramShape.TYPE_POINT_MARKER) {
				pointMarkers.add(shape);
				continue;
			} 
			if(shape.getType() == DiagramShape.TYPE_STORAGE) {
				continue;
			} 
			if(shape.getType() == DiagramShape.TYPE_CUSTOM){
				renderCustomShape(shape, g2);
				continue;
			}

			if(shape.getPoints().isEmpty()) continue;

			int size = shape.getPoints().size();
			
			GeneralPath path;
			path = shape.makeIntoRenderPath(diagram);
			
			//fill
			if(path != null && shape.isClosed() && !shape.isStrokeDashed()){
				if(shape.getFillColor() != null)
					g2.setColor(shape.getFillColor());
				else
					g2.setColor(Color.white);
				g2.fill(path);
			}
			
			//draw
			if(shape.getType() != DiagramShape.TYPE_ARROWHEAD){
				g2.setColor(shape.getStrokeColor());
				if(shape.isStrokeDashed())
					g2.setStroke(dashStroke);
				else
					g2.setStroke(normalStroke);
				g2.draw(path);
			}
		}
		
		//render point markers
		
		g2.setStroke(normalStroke);
		shapesIt = pointMarkers.iterator();
		while(shapesIt.hasNext()){
			DiagramShape shape = (DiagramShape) shapesIt.next();
			//if(shape.getType() != DiagramShape.TYPE_POINT_MARKER) continue;

			GeneralPath path;
			path = shape.makeIntoRenderPath(diagram);
			
			g2.setColor(Color.white);
			g2.fill(path);
			g2.setColor(shape.getStrokeColor());
			g2.draw(path);
		}		
		
		//handle text
		//g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		//renderTextLayer(diagram.getTextObjects().iterator());
		
		renderText(diagram, g2);
		
		if(options.renderDebugLines() || DEBUG){
			Stroke debugStroke =
			  new BasicStroke(
				1,
				BasicStroke.CAP_ROUND,
				BasicStroke.JOIN_ROUND
			  );
			g2.setStroke(debugStroke);
			g2.setColor(new Color(170, 170, 170));
			g2.setXORMode(Color.white);
			for(int x = 0; x < diagram.getPxWidth(); x += diagram.getCellWidth())
				g2.drawLine(x, 0, x, diagram.getPxHeight());
			for(int y = 0; y < diagram.getPxHeight(); y += diagram.getCellHeight())
				g2.drawLine(0, y, diagram.getPxWidth(), y);
		}

		g2.dispose();

		RenderedImage renderedImage = image;
		return renderedImage;
	}

	/*
	 * Renders drop shadows of shapes in diagram onto image.
	 * @requires: all arguments are non-null. 'image' contains a blank white background and nothing else.
	 * @ensures: a shadow of every shape in diagram.getAllDiagramShapes() for which shape.dropsShadow() is rendered
	 * onto a copy of image, which is returned as the result. The shadows are blurry dark-grey copies of their respective
	 * shapes, and offset from the
	 * shape's positions by diagram.getMinimumofCellDimension() / 3.333f. The image is antialiased iff performAntialias
	 * = true
	 */
	private BufferedImage dropShadows(Diagram diagram, BufferedImage image, boolean performAntialias) {
		assert(diagram != null && image != null);

		Graphics2D g2 = image.createGraphics();
		Object antialiasSetting = RenderingHints.VALUE_ANTIALIAS_OFF;
		if(performAntialias)
			antialiasSetting = RenderingHints.VALUE_ANTIALIAS_ON;
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialiasSetting);
		g2.setStroke(new BasicStroke(1, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_ROUND));

		//render shadows
		Iterator shapesIt = diagram.getAllDiagramShapes().iterator();
		while(shapesIt.hasNext()){
			DiagramShape shape = (DiagramShape) shapesIt.next();

			if(shape.getPoints().isEmpty()) continue;

			GeneralPath path = shape.makeIntoRenderPath(diagram);

			float offset = diagram.getMinimumOfCellDimension() / 3.333f;

			if (path != null
					&& shape.dropsShadow()
					&& shape.getType() != DiagramShape.TYPE_CUSTOM){
				GeneralPath shadow = new GeneralPath(path);
				AffineTransform translate = new AffineTransform();
				translate.setToTranslation(offset, offset);
				shadow.transform(translate);
				g2.setColor(new Color(150,150,150));
				g2.fill(shadow);
			}
		}

		//blur shadows
		int blurRadius = 6;
		int blurRadius2 = blurRadius * blurRadius;
		float blurRadius2F = blurRadius2;
		float weight = 1.0f / blurRadius2F;
		float[] elements = new float[blurRadius2];
		for (int k = 0; k < blurRadius2; k++)
			elements[k] = weight;
		Kernel myKernel = new Kernel(blurRadius, blurRadius, elements);

		//if EDGE_NO_OP is not selected, EDGE_ZERO_FILL is the default which creates a black border
		ConvolveOp simpleBlur =
				new ConvolveOp(myKernel, ConvolveOp.EDGE_NO_OP, null);

		BufferedImage destination =
				new BufferedImage(
						image.getWidth(),
						image.getHeight(),
						image.getType());

		simpleBlur.filter(image, (BufferedImage) destination);
		g2.dispose();
		return destination;
	}

	/*
	 * @requires Arguments are not null. this.normalStroke and this.dashStroke are not null. shape.type = TYPE_CUSTOM
	 * and the PNG or SVG asset defining the custom shape is available at the location specified by shape.getDefinition().getFilename().
	 * @ensures The custom shape is rendered onto g2.
	 */
	private void renderCustomShape(DiagramShape shape, Graphics2D g2){
		assert(shape != null && g2 != null && this.normalStroke != null && this.dashStroke != null &&
				shape.getType() == DiagramShape.TYPE_CUSTOM && new File(shape.getDefinition().getFilename()).exists());

		CustomShapeDefinition definition = shape.getDefinition();
		
		Rectangle bounds = shape.getBounds();
		
		if(definition.hasBorder()){
			g2.setColor(shape.getStrokeColor());
			if(shape.isStrokeDashed())
				g2.setStroke(dashStroke);
			else
				g2.setStroke(normalStroke);
			g2.drawLine(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y);
			g2.drawLine(bounds.x + bounds.width, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height);
			g2.drawLine(bounds.x, bounds.y + bounds.height, bounds.x + bounds.width, bounds.y + bounds.height);
			g2.drawLine(bounds.x, bounds.y, bounds.x, bounds.y + bounds.height);
			
//			g2.drawRect(bounds.x, bounds.y, bounds.width, bounds.height); //looks different!			
		}
		
		//TODO: custom shape distintion relies on filename extension. Make this more intelligent
		if(definition.getFilename().endsWith(".png")){
			renderCustomPNGShape(shape, g2);
		} else if(definition.getFilename().endsWith(".svg")){
			renderCustomSVGShape(shape, g2);
		}
	}

	/*
	 * @requires Arguments are not null. shape.getDefiniton().getFilename() exists and is an SVG file.
	 * @ensures Renders the shape defined by file at shape.getDefinition().getFilename() fit to shape.getBounds() on g2.
	 *
	 */
	private void renderCustomSVGShape(DiagramShape shape, Graphics2D g2){
		assert(shape != null && g2 != null && new File(shape.getDefinition().getFilename()).exists() &&
				shape.getDefinition().getFilename().endsWith(".svg"));

		CustomShapeDefinition definition = shape.getDefinition();
		Rectangle bounds = shape.getBounds();
		Image graphic;
		try {
			if(shape.getFillColor() == null) {
				graphic = ImageHandler.instance().renderSVG(
						definition.getFilename(), bounds.width, bounds.height, definition.stretches());
			} else {
				graphic = ImageHandler.instance().renderSVG(
						definition.getFilename(), bounds.width, bounds.height, definition.stretches(), IDREGEX, shape.getFillColor());				
			}
			g2.drawImage(graphic, bounds.x, bounds.y, null);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/*
	 * @requires Arguments are not null. shape.getDefiniton().getFilename() exists and is an PNG file.
	 * @ensures Renders the shape defined by file at shape.getDefinition().getFilename() fit to shape.getBounds() on g2.
	 */
	private void renderCustomPNGShape(DiagramShape shape, Graphics2D g2){
		assert(shape != null && g2 != null && new File(shape.getDefinition().getFilename()).exists() &&
				shape.getDefinition().getFilename().endsWith(".png"));

		CustomShapeDefinition definition = shape.getDefinition();
		Rectangle bounds = shape.getBounds();
		Image graphic = ImageHandler.instance().loadImage(definition.getFilename());
		
		int xPos, yPos, width, height;
		
		if(definition.stretches()){ //occupy all available space
			xPos = bounds.x; yPos = bounds.y;
			width = bounds.width; height = bounds.height;
		} else { //decide how to fit
			int newHeight = bounds.width * graphic.getHeight(null) / graphic.getWidth(null);
			if(newHeight < bounds.height){ //expand to fit width
				height = newHeight;
				width = bounds.width;
				xPos = bounds.x;
				yPos = bounds.y + bounds.height / 2 - graphic.getHeight(null) / 2;
			} else { //expand to fit height
				width = graphic.getWidth(null) * bounds.height / graphic.getHeight(null);
				height = bounds.height;
				xPos = bounds.x + bounds.width / 2 - graphic.getWidth(null) / 2;
				yPos = bounds.y;
			}
		}
		
		g2.drawImage(graphic, xPos, yPos, width, height, null);		
	}

	/*
	 * Renders text.
	 * @requires arguments are non-null
	 * @ensures every TextObject in diagram.getTextObjects() is drawn onto g2.
	 */
	private void renderText(Diagram diagram, Graphics2D g2) {
		assert (diagram != null && g2 != null);

		Iterator textIt = diagram.getTextObjects().iterator();
		while(textIt.hasNext()){
			DiagramText text = (DiagramText) textIt.next();
			g2.setFont(text.getFont());
			if(text.hasOutline()){
				g2.setColor(text.getOutlineColor());
				g2.drawString(text.getText(), text.getXPos() + 1, text.getYPos());
				g2.drawString(text.getText(), text.getXPos() - 1, text.getYPos());
				g2.drawString(text.getText(), text.getXPos(), text.getYPos() + 1);
				g2.drawString(text.getText(), text.getXPos(), text.getYPos() - 1);
			}
			g2.setColor(text.getColor());
			g2.drawString(text.getText(), text.getXPos(), text.getYPos());
		}
	}

	/*
	 * @requires Argument is not null
	 * @ensures \result = true <==> no color channel in color is greater than 200.
	 */
	public static boolean isColorDark(Color color){
		assert (color != null);

		int brightness = Math.max(color.getRed(), color.getGreen());
		brightness = Math.max(color.getBlue(), brightness);
		if(brightness < 200) {
			if(DEBUG) System.out.println("Color "+color+" is dark");
			return true;
		}
		if(DEBUG) System.out.println("Color "+color+" is not dark");
		return false;
	}
}
