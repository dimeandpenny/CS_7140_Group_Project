package org.stathissideris.ascii2image.core;

import org.stathissideris.ascii2image.graphics.BitmapRenderer;
import org.stathissideris.ascii2image.graphics.Diagram;
import org.stathissideris.ascii2image.text.TextGrid;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;


import static javax.swing.JFrame.EXIT_ON_CLOSE;
import static javax.swing.JOptionPane.ERROR_MESSAGE;
import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS;

public class GUI {

    //Some variable for the GUI used to get User input...
    private static JTextField SelectedFileName;
    private static JTextField SaveFileName;
    private static JTextField CommandLineArgs;
    private static JButton buttonLoad;
    private static JButton buttonSavePng;
    private static JButton buttonSaveJpg;
    private static JFrame Main_Frame;
    private static File savepngfile = null;
    private static File savejpgfile = null;
    private static boolean png_trigger = false;
    private static boolean jpg_trigger = false;
    private static Path savepath;
    private static JButton buttonRun;
    private static File Loadfile = null;
    private static TextGrid grid = new TextGrid();

    //This is the TextArea for user notification...
    private static JTextArea Program_Output = new JTextArea(15, 30);

    //This is the "function" that is used to overwrite the System.out.println
    //command to redirect output to the TextArea...
    private static TextAreaOutputStream taOutputStream = new TextAreaOutputStream(
            Program_Output, "Output Messages");

    //Let's create the GUI for the Server...
    public static void CreateDITAAGUI()
    {
        //Let's create the main frame and give it a title...
        JFrame Sort_Server_Frame = new JFrame("Diagrams Through ASCII Art (DITAA) GUI");
        Sort_Server_Frame.setDefaultCloseOperation(EXIT_ON_CLOSE);

        //Let's set the Layout for the Form...
        Sort_Server_Frame.setLayout(new BorderLayout());

        //Here we set the layout for the User Input Area...
        JPanel northPanel = new JPanel(new GridLayout(1, 2));

        //Let's add the panel to the designated location on the form...
        northPanel.add(Create_Button_Panel());

        //Now we set the layout for the Button area...
        JPanel middlePanel = new JPanel(new GridLayout(1, 1));

        //Let's add the panel to the designated location on the form...
        middlePanel.add(Create_Input_Panel());

        //Here we set the layout for the User notification area...
        JPanel southPanel = new JPanel(new GridLayout());

        //Let's add the panel to the designated location on the form...
        southPanel.add(Create_ScrollBox_Panel());

        //Now we add the individual panels to the main Form...
        Sort_Server_Frame.add(northPanel, BorderLayout.NORTH);
        Sort_Server_Frame.add(middlePanel, BorderLayout.CENTER);
        Sort_Server_Frame.add(southPanel, BorderLayout.SOUTH);

        //This centers the GUI on the main screen
        Sort_Server_Frame.setLocationRelativeTo(null);
        Sort_Server_Frame.pack();
        Sort_Server_Frame.setVisible(true);

        //Sort_Server_Frame.addWindowListener((WindowListener) new SortServer.WindowAdapter());
        //I don't like this in the middle of my function.  I tried moving out, but it broke
        Sort_Server_Frame.addWindowListener(new WindowAdapter()
        {
            @Override
            public void windowClosing(WindowEvent e)
            {
                //Add message box here...
                System.exit(0);
            }
        });

    }

    //This creates the User Input Panel...
    public static JPanel Create_Input_Panel()
    {
        //Create the InputPanel
        JPanel InputPanel = new JPanel();

        // set panel layout
        InputPanel.setLayout(new FlowLayout(FlowLayout.CENTER));

        JLabel SelectedFileLabel = new JLabel("Selected File:");

        JLabel SaveFileLabel = new JLabel("Save File:");

        JLabel CommandLineLabel = new JLabel("Optional Command Line arguements:");

        // create text field for each name label
        CommandLineArgs = new JTextField(10);
        SelectedFileName = new JTextField(10);
        SaveFileName = new JTextField(10);

        //add components to panel
        InputPanel.add(SelectedFileLabel);
        InputPanel.add(SelectedFileName);
        InputPanel.add(SaveFileLabel);
        InputPanel.add(SaveFileName);
        //InputPanel.add(CommandLineLabel);
        //InputPanel.add(CommandLineArgs);

        return InputPanel;
    }

    //This creates the Button panel...
    public static JPanel Create_Button_Panel()
    {
        //Create the InputPanel
        JPanel ButtonPanel = new JPanel();

        // set panel layout
        ButtonPanel.setLayout(new FlowLayout(FlowLayout.CENTER));

        buttonLoad = new JButton("Load ASCII File");
        buttonSavePng = new JButton("Save As *.png");
        buttonSaveJpg = new JButton("Save As *.jpg");
        buttonRun = new JButton("Execute");
        JButton buttonExit = new JButton("Exit");


        buttonLoad.addActionListener(new LoadButtonListener());
        buttonSavePng.addActionListener(new SavePngButtonListener());
        buttonSaveJpg.addActionListener(new SaveJpgButtonListener());
        buttonRun.addActionListener(new RunButtonListener());
        buttonExit.addActionListener(new ExitButtonListener());

        ButtonPanel.add(buttonLoad);
        ButtonPanel.add(buttonSavePng);
        ButtonPanel.add(buttonSaveJpg);
        ButtonPanel.add(buttonRun);
        ButtonPanel.add(buttonExit);

        return ButtonPanel;

    }

    //This will create a callback for the Start button, which does some error
    //checking and then call PerformSort...
    private static class LoadButtonListener implements ActionListener
    {
        public void actionPerformed(ActionEvent e)
        {

                JFileChooser filedialog = new JFileChooser("Please Choose a file");

                    int returnVal = filedialog.showOpenDialog(Main_Frame);

                    if (returnVal == JFileChooser.APPROVE_OPTION)
                    {
                        Loadfile = filedialog.getSelectedFile();
                        Path Loadpi = Paths.get(Loadfile.toString() );
                        SelectedFileName.setText(Loadpi.toString());
                    }
                    else
                    {
                        JOptionPane.showMessageDialog(null, "Error opening the file.", "File Open Error: ", JOptionPane.INFORMATION_MESSAGE);
                    }


        }
    }

    //This will create a callback for the Start button, which does some error
    //checking and then call PerformSort...
    private static class SavePngButtonListener implements ActionListener
    {
        public void actionPerformed(ActionEvent e)
        {

            JFileChooser savepngfiledialog = new JFileChooser("Please Input a filename to save...");

            savepngfiledialog.setFileFilter(new FileNameExtensionFilter("png", ".png"));
            savepngfiledialog.setMultiSelectionEnabled(false);

            int returnVal = savepngfiledialog.showSaveDialog(Main_Frame);

            if (returnVal == JFileChooser.APPROVE_OPTION)
            {
                savepngfile = savepngfiledialog.getSelectedFile();

                // Get the location and save a copy
                File selectedFile = savepngfiledialog.getSelectedFile();
                String filename = savepngfiledialog.getSelectedFile().toString();
                Path pi = Paths.get(selectedFile.toString() );
                String ext[];

                if(filename.lastIndexOf(".") != -1 && filename.lastIndexOf(".") != 0)
                {
                    ext = filename.split("\\.");
                    filename = ext[0];
                    if(!ext[1].equals("png") )
                    {
                        JOptionPane.showMessageDialog(Main_Frame, "Invalid file extension.  Saving with PNG extension.", "Invalid Extension", JOptionPane.WARNING_MESSAGE);
                        pi = Paths.get(filename + ".png");
                    }
                    else
                    {
                        pi = Paths.get(filename + ".png");
                    }

                }
                else if (filename.lastIndexOf(".") == -1)
                {
                    pi = Paths.get(selectedFile.toString() + ".png");
                }
                SaveFileName.setText(pi.toString());

                savepath = pi;

                //Let's create a trigger to tell the main program we are saving as a *.png file and return the filename
                png_trigger = true;

                //Let's also disable saving as a *.jpg
                buttonSaveJpg.setEnabled(false);

            }
            else
            {
                JOptionPane.showMessageDialog(null, "Saving *.png file aborted.", "Save PNG Error: ", JOptionPane.INFORMATION_MESSAGE);
            }

        }
    }

    //This will create a callback for the Start button, which does some error
    //checking and then call PerformSort...
    private static class SaveJpgButtonListener implements ActionListener
    {
        public void actionPerformed(ActionEvent e)
        {

            JFileChooser savejpgfiledialog = new JFileChooser("Please Input a filename to save...");
            savejpgfiledialog.setFileFilter(new FileNameExtensionFilter("jpg", ".jpg"));
            savejpgfiledialog.setMultiSelectionEnabled(false);

            int returnVal = savejpgfiledialog.showSaveDialog(Main_Frame);

            if (returnVal == JFileChooser.APPROVE_OPTION)
            {
                savejpgfile = savejpgfiledialog.getSelectedFile();

                // Get the location and save a copy
                File selectedFile = savejpgfiledialog.getSelectedFile();
                String filename = savejpgfiledialog.getSelectedFile().toString();
                Path pi = Paths.get(selectedFile.toString() );
                String ext[];

                if(filename.lastIndexOf(".") != -1 && filename.lastIndexOf(".") != 0)
                {
                    ext = filename.split("\\.");
                    filename = ext[0];
                    if(!ext[1].equals("jpg") )
                    {
                        JOptionPane.showMessageDialog(Main_Frame, "Invalid file extension.  Saving with JPG extension.", "Invalid Extension", JOptionPane.WARNING_MESSAGE);
                        pi = Paths.get(filename + ".jpg");
                    }
                    else
                    {
                        pi = Paths.get(filename + ".jpg");
                    }
                }
                else if (filename.lastIndexOf(".") == -1)
                {
                    pi = Paths.get(selectedFile.toString() + ".jpg");
                }

                savepath = pi;

                SaveFileName.setText(pi.toString());

                //Let's create a trigger to tell the main program we are saving as a *.jpg file and return the filename
                jpg_trigger = true;

                //Let's also disable saving as a *.png
                buttonSavePng.setEnabled(false);

            }
            else
            {
                JOptionPane.showMessageDialog(null, "Saving *.jpg file aborted.", "Save JPG Error: ", JOptionPane.INFORMATION_MESSAGE);
            }


        }
    }

    //This will create a callback for the Start button, which does some error
    //checking and then call PerformSort...
    private static class RunButtonListener implements ActionListener
    {
        public void actionPerformed(ActionEvent e)
        {

            //String txtCommandArgs = CommandLineArgs.getText();
            String txtCommandArgs = "";

            ConversionOptions options = new ConversionOptions();
            ConversionOptions final_options = new ConversionOptions();

            if(txtCommandArgs.trim().isEmpty() )
            {
                final_options = options;
            }
            else
            {
                //need parse command string here...
                String delims = "[ ]+";
                String[] commandLinetokens = null;
                commandLinetokens = txtCommandArgs.split(delims);
                for(int i=0; i<commandLinetokens.length;i++)
                {
                    System.out.println("Command parm: " + commandLinetokens[i]);
                }
                final_options = GUICommandParser(commandLinetokens, options, Loadfile);
            }

            //Here we start it without command line arguements...
            //JOptionPane.showMessageDialog(null, "Please Input a Server Name", "Invaid Server Name", ERROR_MESSAGE);
            buttonLoad.setEnabled(false);
            //Here we need to send in the file
            Start_DITAA_GUI(Loadfile, savepath, final_options);

        }
    }

    //This will create a callback for the Clear button, which erases the TextArea...
    private static class ExitButtonListener implements ActionListener
    {
        public void actionPerformed(ActionEvent e)
        {
            JOptionPane.showMessageDialog(null, "Program Aborted.", "Program Aborted", ERROR_MESSAGE);
            System.exit(0);
        }

    }

    //Creates the User notification area...
    public static JPanel Create_ScrollBox_Panel()
    {
        //Create the InputPanel
        JPanel ScrollPanel = new JPanel();

        // set panel layout
        ScrollPanel.setLayout(new FlowLayout(FlowLayout.CENTER));

        Program_Output.setEditable(false);

        JScrollPane OutputScroll = new JScrollPane(Program_Output);
        OutputScroll.setPreferredSize(new Dimension(500, 200));

        OutputScroll.setHorizontalScrollBarPolicy(HORIZONTAL_SCROLLBAR_ALWAYS);
        OutputScroll.setVerticalScrollBarPolicy(VERTICAL_SCROLLBAR_ALWAYS);

        ScrollPanel.add(OutputScroll);

        System.setOut(new PrintStream(taOutputStream));

        return ScrollPanel;
    }

    //This is the class which redirects the System.out.println output
    //to the GUI...
    private static class TextAreaOutputStream extends OutputStream
    {

        private final JTextArea textArea;
        private final StringBuilder sb = new StringBuilder();
        private String title;

        private TextAreaOutputStream(final JTextArea textArea, String title)
        {
            this.textArea = textArea;
            this.title = title;
            sb.append(title + "> ");
        }

        @Override
        public void flush()
        {
        }

        @Override
        public void close()
        {
        }

        @Override
        public void write(int b) throws IOException
        {

            if (b == '\r')
                return;

            if (b == '\n')
            {
                final String text = sb.toString() + "\n";
                textArea.append(text);
                sb.setLength(0);
                sb.append(title + "> ");
                return;
            }

            sb.append((char) b);
        }
    }


    //This is where
    public static void Start_DITAA_GUI( File file, Path savepath, ConversionOptions options )
    //public static void Start_Server( String name, String number, String Location, String Port )
    {
            try
            {
                //Let's clear the user notification area...
                Program_Output.getDocument().remove(0, Program_Output.getDocument().getLength());
            }
            catch (BadLocationException ex)
            {
                //Let's tell the user there was an error clearing the screen...
                JOptionPane.showMessageDialog(null, "Error clearing screen: " + ex.toString(), "Error clearing screen.", JOptionPane.ERROR_MESSAGE);
                buttonLoad.setEnabled(true);
            }

            System.out.println( "DITAA is executing..." );

            System.out.println( "Current File is: " + file );

            //Here we load the input file into a new grid object
            try
            {
                grid.loadFrom(Loadfile.toString());
            } catch (IOException e)
            {
                e.printStackTrace();
            }

            Diagram diagram = new Diagram(grid, options);

            RenderedImage image = new BitmapRenderer().renderToImage(diagram, options.renderingOptions);

            //Let's get the final save path for the image...
            File finalSavePath = new File(savepath.toString());

            //Create a variable to hold the file format...
            String saveformat;

            //Here we check to see what format to save the file using ImageIO.write...
            if(jpg_trigger == true)
            {
                saveformat = "jpg";
            }
            else
            {
                saveformat = "png";
            }

            //Here we save the Rendered Image...
            try
            {
                ImageIO.write(image, saveformat, finalSavePath);
            } catch (IOException e)
            {
                e.printStackTrace();
            }

        System.out.println( "Program Complete.");
        //Set the button back to enable...
        buttonLoad.setEnabled(true);
        buttonRun.setEnabled(true);
        buttonSaveJpg.setEnabled(true);
        buttonSavePng.setEnabled(true);
        jpg_trigger = false;
        png_trigger = false;
    }

    //Here we parse the optional commandline arguments from the GUI...
    public static ConversionOptions GUICommandParser(String[] args, ConversionOptions options, File filein)
    {
        // Convert String Array to List
        List<String> list = Arrays.asList(args);

        //Let's create the return variable to save the updated options, since I can't use pass by reference
        ConversionOptions final_tokens = new ConversionOptions();

        //Now we need to parse the Command List...
        if(list.contains("d") || list.contains("debug"))
        {
            options.processingOptions.setPrintDebugOutput(true);
            System.out.println("Running with options: ");
            for(int i=0;i<list.size();i++)
            {
                System.out.println(list.get(i).toString());
            }
            System.out.println("Using grid:");
            try {
                if(!grid.loadFrom(filein.getPath().toString(), options.processingOptions))
                {
                    System.err.println("Cannot open file "+ filein +" for reading");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            grid.printDebug();
        }
        if(list.contains("background"))
        {
            int index8 = list.indexOf("background");
            Color background = Color.decode(list.get(index8+1));
            options.renderingOptions.setBackgroundColor(background);
        }
        if(list.contains("fixed-slope"))
        {
            options.renderingOptions.setFixedSlope(true);
        }
        if(list.contains("t") || list.contains("transparent"))
        {
            options.renderingOptions.setBackgroundColor((new Color(0,0,0,0)));
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
        if(list.contains("svg") )
        {
            options.renderingOptions.setImageType(RenderingOptions.ImageType.SVG);
        }
        if(list.contains("svg-font-url") )
        {
            int index5 = list.indexOf("svg-font-url");
            int svg_temp = Integer.valueOf(list.get(index5+1));
            options.processingOptions.setColorCodesProcessingMode(svg_temp);
        }

        return final_tokens = options;


    }
    public static void Start_DITAA_CommandLine(String args[])
    {
        System.out.println("Running in CommandLine mode...");
        CommandLineConverter.main(args);

        System.out.println("Program completed...");


    }

    //This is the main function which is used to display the GUI and
    public static void main( String args[]) throws Exception
    {

        //Use the command line
        if(args.length > 0)
        {
            Start_DITAA_CommandLine(args);
        }
        //Use the GUI
        else
        {
            //Let's call the function to create the GUI...
            CreateDITAAGUI();

            /*
            //FOR TESTING
            String[] args2 = new String[2];
            args2[0] = "C:\\art3.txt";
            args2[1] = "-d";

            Start_DITAA_CommandLine(args2);
            */

        }

    }

}
