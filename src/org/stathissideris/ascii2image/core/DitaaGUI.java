package org.stathissideris.ascii2image.core;

import org.apache.commons.cli.*;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;


public class DitaaGUI{
    // the main window
    private JFrame frame;
    // the location of the currently loaded source file
    private String sourceFilePath;
    // a boolean indicating true when there is a source file and it is readable
    private boolean sourceFileValid = false;
    // the location in the temp dir where ditaa has generated the image (once a valid source is loaded)
    private String tempOutputLoc;
    // keep a reference to the output image label and save buttons at the class level so that they can be accessed
    // by both sub-panels
    private JLabel outputImagePanel;
    private JButton jpegsaveFileButton;
    private JButton pngsaveFileButton;


    // the options to pass to DITAA
    ConversionOptions options = new ConversionOptions();

    public DitaaGUI(){
        this.frame = new JFrame("DITAA GUI");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        this.frame.getContentPane().setLayout(new BoxLayout(this.frame.getContentPane(), BoxLayout.LINE_AXIS));

        JSplitPane mainSplit = new JSplitPane();
        mainSplit.setLeftComponent(this.createSourceContentPane());
        mainSplit.setRightComponent(this.createOutputContentPane());

        this.frame.getContentPane().add(mainSplit);
        this.frame.pack();
    }

    public DitaaGUI(ConversionOptions opts){
        this();

        this.options = opts;
    }

    /**
     * Opens the DITAA GUI
     */
    public void openGUI()
    {

        this.frame.setVisible(true);
    }

    /**
     * Creates the Source panel which provides the functions of choosing/loading a file and displaying its contents.
     *
     * @return the JPanel representing the source panel
     */
    private JPanel createSourceContentPane()
    {
        JPanel sourcePanel = new JPanel();
        sourcePanel.setLayout(new BoxLayout(sourcePanel, BoxLayout.PAGE_AXIS));

        //Create a menu bar
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        final JMenuItem MenuExit = new JMenuItem("Exits the DITAA program.");
        MenuExit.setToolTipText("Exit application");
        final JMenuItem MenuHelp = new JMenuItem("How to Use");
        MenuHelp.setToolTipText("Explains how to use the program.");

        MenuExit.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent actionEvent)
            {
                if (actionEvent.getSource() == MenuExit)
                {
                    // if "cancel" or the 'x' was used to close the dialog, go no further
                    JOptionPane.showMessageDialog(DitaaGUI.this.frame, "Program will now exit.");
                    System.exit(0);
                }
            }
        });

        MenuHelp.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent actionEvent)
            {
                if (actionEvent.getSource() == MenuHelp)
                {
                    // if "cancel" or the 'x' was used to close the dialog, go no further
                    JOptionPane.showMessageDialog(DitaaGUI.this.frame, "How to Use the DITAA program:\n" +
                            "Step 1. Press the Load File Button to display a file browser window\n" +
                            "Step 2. Navigate to the location of the ASCII art file.\n" +
                            "Step 3. Select the ASCII art file and press Open.\n" +
                            "Step 4. The contents of the ASCII file will be shown on the left and a preview of the converted file will be shown on the right.\n" +
                            "Step 5. Choose to save the converted file as either a *.png or a *.jpg by selecting the appropriate button.\n" +
                            "Step 6. Navigate to the desired location to save the file.\n" +
                            "Step 7. Enter a filename to save the converted ASCII art.\n" +
                            "Step 8. Press the Save button.\n" +
                            "Step 9. Either repeat the steps above to convert a new file or exit the program by pressing the X in the upper right corner."
                            , "How to use the DITAA program", JOptionPane.INFORMATION_MESSAGE);

                    return;
                }
            }
        });

        fileMenu.add(MenuHelp);
        fileMenu.add(MenuExit);
        menuBar.add(fileMenu);
        sourcePanel.add(menuBar);


        // Add the text area to display the file contents
        final JTextArea sourceTextArea = new JTextArea();
        sourceTextArea.setRows(30);
        sourceTextArea.setFont(new Font("monospaced", Font.PLAIN, 12));
        sourceTextArea.setEditable(false);

        JScrollPane scrollPane = new JScrollPane(sourceTextArea);
        sourcePanel.add(scrollPane);

        // Add a input box to enter command line information
        JPanel commandPanel = new JPanel();
        commandPanel.setLayout(new BoxLayout(commandPanel, BoxLayout.LINE_AXIS));

        // Add a place to show the selected file name
        JLabel commandlineLabel = new JLabel();
        commandlineLabel.setText("Optional Command line inputs: ");
        commandPanel.add(commandlineLabel);


        final JTextField commandField = new JTextField(20);
        commandField.setEditable(true);
        commandPanel.add(commandField);

        sourcePanel.add(commandPanel);



        // Add the button to load a file and label to show the selected file name
        JPanel filePanel = new JPanel();
        filePanel.setLayout(new BoxLayout(filePanel, BoxLayout.LINE_AXIS));

        // Add a place to show the selected file name
        JLabel sourceFileLabel = new JLabel();
        sourceFileLabel.setText("Selected File: ");
        filePanel.add(sourceFileLabel);



        final JTextField filenameField = new JTextField(20);
        filenameField.setEditable(false);
        filePanel.add(filenameField);

        // Add the text area to display the file contents
        JLabel outputLabel = new JLabel();
        outputLabel.setText("Program Output Messages: ");
        sourcePanel.add(outputLabel);

        final JTextArea outputTextArea = new JTextArea();
        outputTextArea.setRows(4);
        outputTextArea.setFont(new Font("monospaced", Font.PLAIN, 12));
        outputTextArea.setEditable(false);

        JScrollPane outputscrollPane = new JScrollPane(outputTextArea);
        sourcePanel.add(outputscrollPane);

        /**
         * A button to load the file - most of the logic is driven by the following action which is performed after
         * the file is selected and loaded:
         * 1. file loaded
         * 2. file read
         * if file is readable:
         * 3. put contents into text area
         * 4. run ditaa
         * 5. load ditaa output into the results panel
         */
        final JButton loadFileButton = new JButton("Load file...");
        loadFileButton.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent actionEvent)
            {
                if(actionEvent.getSource() == loadFileButton)
                {

                    // Open a file chooser
                    JFileChooser fileChooser = new JFileChooser();
                    int chooserReturn = fileChooser.showOpenDialog(DitaaGUI.this.frame);
                    if(chooserReturn != JFileChooser.APPROVE_OPTION)
                    {
                        // if "cancel" or the 'x' was used to close the dialog, go no further
                        JOptionPane.showMessageDialog(DitaaGUI.this.frame, "No File was chosen.", "Notification", JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }
                    File selectedFile = fileChooser.getSelectedFile();

                    // set the name of the file
                    DitaaGUI.this.sourceFilePath = selectedFile.getAbsolutePath();
                    filenameField.setText(DitaaGUI.this.sourceFilePath);

                    //open the file and set it's contents in the text area
                    String fileContents;
                    try
                    {
                        fileContents = new String(Files.readAllBytes(Paths.get(DitaaGUI.this.sourceFilePath)));
                        DitaaGUI.this.sourceFileValid = true;
                    }
                    catch(IOException | InvalidPathException e)
                    {
                        fileContents = "<< Unable to read selected file :\n" +
                                e.getMessage() +
                                "\n>>";
                        DitaaGUI.this.sourceFileValid = false;
                    }
                    sourceTextArea.setText(fileContents);

                    // Run DITAA
                    if(DitaaGUI.this.sourceFileValid)
                    {
                        //Uncomment this to use command line inputs from the GUI
                        String commandInputs = commandField.getText();

                        //Comment this out to remove the commandline options
                        //String commandInputs = "";

                        try {
                            DitaaGUI.this.tempOutputLoc = DitaaGUI.this.runDitaa(commandInputs, outputTextArea);
                        } catch (UnsupportedEncodingException e) {
                            e.printStackTrace();
                        } catch (ParseException e) {
                            e.printStackTrace();
                        }

                        // load the output image into the output panel
                        DitaaGUI.this.updateOutputImage();
                        DitaaGUI.this.jpegsaveFileButton.setEnabled(true);
                        DitaaGUI.this.pngsaveFileButton.setEnabled(true);
                    }
                    else
                    {
                        // set back to false in case it was enabled previously
                        DitaaGUI.this.clearOutputImage();
                        DitaaGUI.this.jpegsaveFileButton.setEnabled(false);
                        DitaaGUI.this.pngsaveFileButton.setEnabled(false);
                    }
                }
            }
        });
        filePanel.add(loadFileButton);

        sourcePanel.add(filePanel);

        return sourcePanel;
    }

    private JPanel createOutputContentPane()
    {
        JPanel sourcePanel = new JPanel();
        sourcePanel.setLayout(new BoxLayout(sourcePanel, BoxLayout.PAGE_AXIS));

        // Add the text area to display the file contents
        this.outputImagePanel = new JLabel();
        JScrollPane scrollPane = new JScrollPane(outputImagePanel);
        sourcePanel.add(scrollPane);

        this.jpegsaveFileButton = new JButton("Save As jpeg...");
        jpegsaveFileButton.setEnabled(false); //(disable until a file is loaded)
        jpegsaveFileButton.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent actionEvent)
            {
                if(actionEvent.getSource() == jpegsaveFileButton)
                {
                    JFileChooser fileChooser = new JFileChooser();

                    fileChooser.setFileFilter(new FileNameExtensionFilter("jpg", ".jpg"));
                    fileChooser.setMultiSelectionEnabled(false);

                    int chooserReturn = fileChooser.showSaveDialog(DitaaGUI.this.frame);

                    if(chooserReturn != JFileChooser.APPROVE_OPTION)
                    {
                        // if "cancel" or the 'x' was used to close the dialog, go no further
                        JOptionPane.showMessageDialog(DitaaGUI.this.frame, "No File was saved.", "Notification", JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }

                    // Get the location and save a copy
                    File selectedFile = fileChooser.getSelectedFile();
                    String filename = fileChooser.getSelectedFile().toString();
                    Path pi = Paths.get(selectedFile.toString() );
                    String ext[];

                    if(filename.lastIndexOf(".") != -1 && filename.lastIndexOf(".") != 0)
                    {
                        JOptionPane.showMessageDialog(DitaaGUI.this.frame, "Invalid file extension.  Saving with .jpg extension.", "Invalid Extension", JOptionPane.WARNING_MESSAGE);
                        ext = filename.split("\\.");
                        filename = ext[0];
                        pi = Paths.get(filename + ".jpg");
                    }
                    else if (filename.lastIndexOf(".") == -1)
                    {
                        pi = Paths.get(selectedFile.toString() + ".jpg");
                    }
                    try
                    {
                        Files.copy(Paths.get(DitaaGUI.this.tempOutputLoc), pi);
                    }
                    catch(IOException e)
                    {
                        System.err.println("Unable to save to location " + selectedFile + ":");
                        System.err.println(e.getMessage());
                        System.err.println(e.getStackTrace());
                    }
                }
            }
        });

        this.pngsaveFileButton = new JButton("Save As png...");
        pngsaveFileButton.setEnabled(false); //(disable until a file is loaded)
        pngsaveFileButton.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent actionEvent)
            {
                if(actionEvent.getSource() == pngsaveFileButton)
                {
                    JFileChooser fileChooser = new JFileChooser();

                    //fileChooser.addChoosableFileFilter(new FileNameExtensionFilter("png", ".png"));
                    fileChooser.setFileFilter(new FileNameExtensionFilter("png", ".png"));
                    fileChooser.setMultiSelectionEnabled(false);


                    int chooserReturn = fileChooser.showSaveDialog(DitaaGUI.this.frame);

                    if(chooserReturn != JFileChooser.APPROVE_OPTION)
                    {
                        // if "cancel" or the 'x' was used to close the dialog, go no further
                        JOptionPane.showMessageDialog(DitaaGUI.this.frame, "No File was saved.", "Notification", JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }

                    // Get the location and save a copy
                    File selectedFile = fileChooser.getSelectedFile();
                    String filename = fileChooser.getSelectedFile().toString();
                    Path pi = Paths.get(selectedFile.toString() );
                    String ext[];

                    if(filename.lastIndexOf(".") != -1 && filename.lastIndexOf(".") != 0)
                    {
                        JOptionPane.showMessageDialog(DitaaGUI.this.frame, "Invalid file extension.  Saving with .png extension.", "Invalid Extension", JOptionPane.WARNING_MESSAGE);
                        ext = filename.split("\\.");
                        filename = ext[0];
                        pi = Paths.get(filename + ".png");
                    }
                    else if (filename.lastIndexOf(".") == -1)
                    {
                        pi = Paths.get(selectedFile.toString() + ".png");
                    }
                    try
                    {
                        Files.copy(Paths.get(DitaaGUI.this.tempOutputLoc), pi);
                    }
                    catch(IOException e)
                    {
                        System.err.println("Unable to save to location " + selectedFile + ":");
                        System.err.println(e.getMessage());
                        System.err.println(e.getStackTrace());
                    }
                }
            }
        });

        sourcePanel.add(pngsaveFileButton);
        sourcePanel.add(jpegsaveFileButton);
        return sourcePanel;
    }

    private String runDitaa(String commandInputs, JTextArea outputtext) throws UnsupportedEncodingException, ParseException {
        // get a temporary file path
        Path outFilePath = null;
        try
        {
            outFilePath = Files.createTempFile("ditaa", ".png");
        } catch (IOException e)
        {
            System.err.println("Failed to create temp output file:");
            System.err.println(e.getMessage());
            System.err.println(e.getStackTrace());
        }


        String delims = "[ ]+";
        String[] command_tokens = commandInputs.split(delims);

        ConversionOptionsGUI(command_tokens, options, outputtext);

        CommandLineConverter.runSimpleModeGUI(options, this.sourceFilePath, outFilePath.toString(), outputtext);
        return outFilePath.toString();
    }

    private void updateOutputImage()
    {
        if(this.outputImagePanel != null && this.sourceFileValid && this.tempOutputLoc != null && this.tempOutputLoc.length() > 0){
            try
            {
                Image preview = ImageIO.read(new File(this.tempOutputLoc));
                this.outputImagePanel.setIcon(new ImageIcon(preview));
            }
            catch(IOException e)
            {
                System.err.println("Unable to update image preview: " +
                        e.getMessage() +
                        e.getStackTrace());

                this.outputImagePanel.setText("Error loading preview.");
            }
        }
    }

    private void clearOutputImage()
    {
        if(this.outputImagePanel != null)
        {
            this.outputImagePanel.setIcon(null);
        }
    }
 //TODO anything this wants to use from CommandLineConverter probably needs factoring out into own class??

    private String[] ParseCommandLine(String[] args, JTextArea text)
    {

        long startTime = System.currentTimeMillis();

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

        String[] parsedCommandArgs = cmdLine.getArgs();

        return args;
        //return parsedCommandArgs;

    }

    private void ConversionOptionsGUI(String[] cmdLine, ConversionOptions options, JTextArea text) throws UnsupportedEncodingException{

        // Convert String Array to List
        List<String> list = Arrays.asList(cmdLine);

        if(list.contains("d") || list.contains("debug")){
            options.processingOptions.setPrintDebugOutput(true);
        }
        if(list.contains("v") || list.contains("verbose")){
            options.processingOptions.setVerbose(true);
        }
        if(list.contains("no-shadows") ){
            options.renderingOptions.setDropShadows(true);
        }
        if(list.contains("overwrite") ){
            options.processingOptions.setOverwriteFiles(true);
        }
        if(list.contains("scale")){
            int index = list.indexOf("scale");
        	float scale = Float.valueOf(list.get(index+1));
			options.renderingOptions.setScale(scale);
		}
        if(list.contains("round-corners") ){
            options.processingOptions.setAllCornersAreRound(true);
        }
        if(list.contains("no-separation") ){
            options.processingOptions.setPerformSeparationOfCommonEdges(true);
        }
        if(list.contains("no-antialias") ){
            options.renderingOptions.setAntialias(false);
        }
        if(list.contains("tabs") ){
            int index2 = list.indexOf("tabs");
            int tabSizeValue = Integer.valueOf(list.get(index2+1));
            if(tabSizeValue < 0) tabSizeValue = 0;
            options.processingOptions.setTabSize(tabSizeValue);
        }
        if(list.contains("encoding") ){
            int index3 = list.indexOf("encoding");
            int encoding_temp = Integer.valueOf(list.get(index3+1));
            options.processingOptions.setColorCodesProcessingMode(encoding_temp);
        }

    }

}

