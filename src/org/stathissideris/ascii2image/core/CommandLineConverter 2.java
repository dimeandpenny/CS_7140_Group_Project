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
package org.stathissideris.ascii2image.core;

import java.awt.*;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

import javax.imageio.ImageIO;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.stathissideris.ascii2image.graphics.BitmapRenderer;
import org.stathissideris.ascii2image.graphics.Diagram;
import org.stathissideris.ascii2image.text.TextGrid;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

/**
 * 
 * @author Efstathios Sideris
 */
public class CommandLineConverter {
		
	private static String notice = "Diagrams Through ASCII Art (DITAA) version 1.0 November 2020";
	
	private static String[] markupModeAllowedValues = {"use", "ignore", "render"};
	
	public static void main(String[] args){
		
		long startTime = System.currentTimeMillis();
		
		System.out.println("\n"+notice+"\n");
		
		Options cmdLnOptions = new Options();
		cmdLnOptions.addOption(
				OptionBuilder.withLongOpt("help")
				.withDescription( "Prints usage help." )
				.create() );
		cmdLnOptions.addOption("v", "verbose", false, "Makes ditaa more verbose.");
		cmdLnOptions.addOption("o", "overwrite", false, "If the filename of the destination image already exists, an alternative name is chosen. If the overwrite option is selected, the image file is instead overwriten.");
		cmdLnOptions.addOption("S", "no-shadows", false, "Turns off the drop-shadow effect.");
		cmdLnOptions.addOption("A", "no-antialias", false, "Turns anti-aliasing off.");
		cmdLnOptions.addOption("d", "debug", false, "Renders the debug grid over the resulting image.");
		cmdLnOptions.addOption("r", "round-corners", false, "Causes all corners to be rendered as round corners.");
		cmdLnOptions.addOption("E", "no-separation", false, "Prevents the separation of common edges of shapes.");
		cmdLnOptions.addOption("h", "html", false, "In this case the input is an HTML file. The contents of the <pre class=\"textdiagram\"> tags are rendered as diagrams and saved in the images directory and a new HTML file is produced with the appropriate <img> tags.");
		
		cmdLnOptions.addOption(
				OptionBuilder.withLongOpt("encoding")
				.withDescription("The encoding of the input file.")
				.hasArg()
				.withArgName("ENCODING")
				.create('e')
				);

		cmdLnOptions.addOption(
				OptionBuilder.withLongOpt("scale")
				.withDescription("A natural number that determines the size of the rendered image. The units are fractions of the default size (2.5 renders 1.5 times bigger than the default).")
				.hasArg()
				.withArgName("SCALE")
				.create('s')
				);

		cmdLnOptions.addOption(
				OptionBuilder.withLongOpt("tabs")
				.withDescription("Tabs are normally interpreted as 8 spaces but it is possible to change that using this option. It is not advisable to use tabs in your diagrams.")
				.hasArg()
				.withArgName("TABS")
				.create('t')
				);

		cmdLnOptions.addOption(
				OptionBuilder.withLongOpt("gui")
						.withDescription("Runs the graphical interface; Not compatible with the --html option.")
						.withArgName("gui")
						.create('g')
		);

//TODO: uncomment this for next version:
//		cmdLnOptions.addOption(
//				OptionBuilder.withLongOpt("config")
//				.withDescription( "The shape configuration file." )
//				.hasArg()
//				.withArgName("CONFIG_FILE")
//				.create('c') );
		
		CommandLine cmdLine = null;
		
		///// parse command line options
		try {
			// parse the command line arguments
			CommandLineParser parser = new PosixParser();
			
			cmdLine = parser.parse(cmdLnOptions, args);
						
			// validate that block-size has been set
			if( cmdLine.hasOption( "block-size" ) ) {
				// print the value of block-size
				System.out.println( cmdLine.getOptionValue( "block-size" ) );
			}
			
		} catch (org.apache.commons.cli.ParseException e) {
			System.err.println(e.getMessage());
			new HelpFormatter().printHelp("java -jar ditaa.jar <inpfile> [outfile]", cmdLnOptions, true);
			System.exit(2);
		}
		
		
		if(cmdLine.hasOption("help")){
			new HelpFormatter().printHelp("java -jar ditaa.jar <inpfile> [outfile]", cmdLnOptions, true);
			System.exit(0);			
		}
						
		ConversionOptions options = null;
		try {
			options = new ConversionOptions(cmdLine);
		} catch (UnsupportedEncodingException e2) {
			System.err.println("Error: " + e2.getMessage());
			System.exit(2);
		}  
		
		args = cmdLine.getArgs();

		//TODO: Start GUI in this case? Fail if they try to use HTML mode with GUI? ~Chris M
		boolean isGUI = false;
		if(args.length == 0) {
			isGUI = true;
			System.out.println("No parameters detected; running in GUI mode.");
		}
		else if(cmdLine.hasOption("gui")){
			isGUI = true;
		}

		/////// print options before running
		System.out.println("Running with options:");
		Option[] opts = cmdLine.getOptions();
		for (Option option : opts) {
			if(option.hasArgs()){
				for(String value:option.getValues()){
					System.out.println(option.getLongOpt()+" = "+value);
				}
			} else if(option.hasArg()){
				System.out.println(option.getLongOpt()+" = "+option.getValue());
			} else {
				System.out.println(option.getLongOpt());
			}
		}

		//TODO: Break up into rendering and saving b/c GUI does those steps differently?

		if(cmdLine.hasOption("html")){

			if (isGUI) {
				System.out.println("No GUI for converting HTML files.");
				throw new NotImplementedException();
			}

			String filename = args[0];
			
			boolean overwrite = false;
			if(options.processingOptions.overwriteFiles()) overwrite = true;
			
			String toFilename;
			if(args.length == 1){
				toFilename = FileUtils.makeTargetPathname(filename, "html", "_processed", true);
			} else {
				toFilename = args[1];
			}
			File target = new File(toFilename);
			if(!overwrite && target.exists()) {
				System.out.println("Error: File "+toFilename+" exists. If you would like to overwrite it, please use the --overwrite option.");
				System.exit(0);
			}
			
			new HTMLConverter().convertHTMLFile(filename, toFilename, "ditaa_diagram", "images", options);
			System.exit(0);
			
		} else { //simple mode

			//TODO make more elegant after refactoring of CommandLineConverter
			if (isGUI) {
				new DitaaGUI(options).openGUI();
				//new DitaaGUI().openGUI();
			}
			else{
				String inFilename = args[0];
				String toFilename;
				if(args.length == 1){
					toFilename = FileUtils.makeTargetPathname(inFilename, "png",
							options.processingOptions.overwriteFiles());
				} else {
					toFilename = args[1];
				}

				CommandLineConverter.runSimpleMode(options, inFilename, toFilename);

				long endTime = System.currentTimeMillis();
				long totalTime  = (endTime - startTime) / 1000;
				System.out.println("Done in "+totalTime+"sec");
			}
		}
	}

	public static void runSimpleMode(ConversionOptions options, String filename, String toFilename){
		TextGrid grid = new TextGrid();
		if(options.processingOptions.getCustomShapes() != null){
			grid.addToMarkupTags(options.processingOptions.getCustomShapes().keySet());
		}

		System.out.println("Reading file: "+filename);
		try {
			if(!grid.loadFrom(filename, options.processingOptions)){
				System.err.println("Cannot open file "+filename+" for reading");
			}
		} catch (UnsupportedEncodingException e1){
			System.err.println("Error: "+e1.getMessage());
			System.exit(1);
		} catch (FileNotFoundException e1) {
			System.err.println("Error: File "+filename+" does not exist");
			System.exit(1);
		} catch (IOException e1) {
			System.err.println("Error: Cannot open file "+filename+" for reading");
			System.exit(1);
		}

		if(options.processingOptions.printDebugOutput()){
			System.out.println("Using grid:");
			grid.printDebug();
		}

		Diagram diagram = new Diagram(grid, options);
		System.out.println("Rendering to file: "+toFilename);


		RenderedImage image = new BitmapRenderer().renderToImage(diagram, options.renderingOptions);

		saveImage(image, toFilename);
	}

	public static void saveImage(RenderedImage image, String toFilename) {
		try {
			File file = new File(toFilename);
			ImageIO.write(image, "png", file);
		} catch (IOException e) {
			//e.printStackTrace();
			System.err.println("Error: Cannot write to file "+toFilename);
			System.exit(1);
		}
	}
}
