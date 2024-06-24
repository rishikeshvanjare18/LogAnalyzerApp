package com.Rishi.LogChecker.LogChecker;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class LogAnalyzerApp extends JFrame {

	private JProgressBar progressBar;
	private JTextField logPatternField;
	private JTextField timestampPatternField;

	public LogAnalyzerApp() {
		setTitle("Log Analyzer");
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		setSize(600, 200);
		setLocationRelativeTo(null); // Center the window

		// Layout components
		progressBar = new JProgressBar(0, 100);
		progressBar.setStringPainted(true);

		JLabel logPatternLabel = new JLabel("Log Pattern (Regex):");
		logPatternField = new JTextField("^(ERROR) \\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2} .*", 40);

		JLabel timestampPatternLabel = new JLabel("Timestamp Pattern (Regex):");
		timestampPatternField = new JTextField("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}", 40);

		JButton analyzeButton = new JButton("Analyze Log File");
		analyzeButton.addActionListener(e -> new Thread(this::analyzeLogFile).start());

		JButton helpButton = new JButton("?");
		helpButton.setFont(new Font("Arial", Font.BOLD, 14));
		helpButton.setToolTipText("Help");
		helpButton.addActionListener(e -> showHelpDialog());

		JPanel inputPanel = new JPanel(new GridBagLayout());
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.anchor = GridBagConstraints.WEST;
		gbc.insets = new Insets(5, 5, 5, 5);
		inputPanel.add(logPatternLabel, gbc);

		gbc.gridy++;
		inputPanel.add(timestampPatternLabel, gbc);

		gbc.gridx = 1;
		gbc.gridy = 0;
		gbc.gridwidth = 2;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		inputPanel.add(logPatternField, gbc);

		gbc.gridy++;
		inputPanel.add(timestampPatternField, gbc);

		gbc.gridy++;
		gbc.gridx = 0;
		gbc.gridwidth = 3;
		gbc.fill = GridBagConstraints.NONE;
		inputPanel.add(analyzeButton, gbc);

		gbc.gridy++;
		inputPanel.add(helpButton, gbc);

		JPanel panel = new JPanel(new BorderLayout());
		panel.add(inputPanel, BorderLayout.NORTH);
		panel.add(progressBar, BorderLayout.SOUTH);

		getContentPane().add(panel);
	}

	private void showHelpDialog() {
		String helpMessage = "Instructions:\n\n"
				+ "1. Define the log pattern and timestamp pattern using regular expressions.\n"
				+ "   - Default log pattern: ^(ERROR) \\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2} .*\n"
				+ "   - Default timestamp pattern: \\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\n"
				+ "2. Click 'Analyze Log File' and select the log file you want to analyze.\n"
				+ "3. The progress bar will indicate the analysis progress.\n"
				+ "4. Once the analysis is complete, an Excel report will be generated and saved to the desktop in a 'logs' folder with a unique filename based on the current timestamp.\n"
				+ "5. A popup message will confirm the report generation.";

		JOptionPane.showMessageDialog(this, helpMessage, "Help", JOptionPane.INFORMATION_MESSAGE);
	}

	private void analyzeLogFile() {
		JFileChooser fileChooser = new JFileChooser();
		int returnValue = fileChooser.showOpenDialog(this);
		if (returnValue == JFileChooser.APPROVE_OPTION) {
			try {
				String filename = fileChooser.getSelectedFile().getAbsolutePath();
				Map<String, Integer> exceptionCounts = new HashMap<>();
				Pattern logPattern = Pattern.compile(logPatternField.getText());
				Pattern timestampPattern = Pattern.compile(timestampPatternField.getText());

				BufferedReader reader = new BufferedReader(new FileReader(filename));
				String line;
				StringBuilder currentException = new StringBuilder();
				boolean inException = false;

				// Count total lines for progress calculation
				int totalLines = 0;
				while (reader.readLine() != null)
					totalLines++;
				reader.close();

				reader = new BufferedReader(new FileReader(filename));
				int processedLines = 0;

				while ((line = reader.readLine()) != null) {
					// Update progress bar
					processedLines++;
					int progress = (int) ((processedLines / (double) totalLines) * 100);
					SwingUtilities.invokeLater(() -> progressBar.setValue(progress));
					// Check for ERROR level followed by date and time
					Matcher matcher = logPattern.matcher(line);
					if (matcher.matches()) {
						// Start of a new ERROR log
						if (inException) {
							// Process previous exception
							processException(currentException.toString(), exceptionCounts, timestampPattern);
							currentException.setLength(0);
						}
						currentException.append(line).append("\n");
						inException = true;
					} else if (inException) {
						// Continue current exception
						currentException.append(line).append("\n");
					} else {
						// Ignore non-ERROR logs
						inException = false;
					}
				}

				// Process the last exception, if any
				if (inException) {
					processException(currentException.toString(), exceptionCounts, timestampPattern);
				}

				reader.close();

				// Generate Excel report
				generateExcelReport(exceptionCounts);

				// Set progress bar to 100% after completion
				SwingUtilities.invokeLater(() -> progressBar.setValue(100));

			} catch (IOException ex) {
				JOptionPane.showMessageDialog(this, "Error reading log file: " + ex.getMessage(), "Error",
						JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	private void processException(String exceptionText, Map<String, Integer> exceptionCounts,
			Pattern timestampPattern) {
		StringBuilder fullException = new StringBuilder();
		String[] lines = exceptionText.split("\n");

		// Start capturing from the first line of the exception
		for (String line : lines) {
			if (line.trim().startsWith("at ") || line.trim().startsWith("Caused by:") || line.startsWith("ERROR")) {
				fullException.append(line.trim()).append("\n");
			}
		}

		// Remove timestamp and consider the remaining part
		String logWithoutTimestamp = timestampPattern.matcher(fullException.toString()).replaceAll("").trim();
		exceptionCounts.put(logWithoutTimestamp, exceptionCounts.getOrDefault(logWithoutTimestamp, 0) + 1);
	}

	private void generateExcelReport(Map<String, Integer> exceptionCounts) {
		Workbook workbook = new XSSFWorkbook();
		Sheet sheet = workbook.createSheet("Exception Report");

		// Create header row
		Row headerRow = sheet.createRow(0);
		headerRow.createCell(0).setCellValue("Exception");
		headerRow.createCell(1).setCellValue("Occurrences");

		// Populate data
		int rowNum = 1;
		for (Map.Entry<String, Integer> entry : exceptionCounts.entrySet()) {
			Row row = sheet.createRow(rowNum++);
			row.createCell(0).setCellValue(entry.getKey());
			row.createCell(1).setCellValue(entry.getValue());
		}

		// Determine file path
		String desktopPath = System.getProperty("user.home") + "/Desktop";
		String logsFolderPath = desktopPath + "/logs";
		String currentDate = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
		String reportFileName = logsFolderPath + "/ExceptionReport_" + currentDate + ".xlsx";

		try {
			// Create logs folder if it doesn't exist
			Path logsFolder = Paths.get(logsFolderPath);
			if (!Files.exists(logsFolder)) {
				Files.createDirectory(logsFolder);
			}

			// Write workbook to file
			try (FileOutputStream outputStream = new FileOutputStream(reportFileName)) {
				workbook.write(outputStream);
				workbook.close();
			}

			JOptionPane.showMessageDialog(this, "Excel report generated successfully: " + reportFileName,
					"Report Generated", JOptionPane.INFORMATION_MESSAGE);

		} catch (IOException ex) {
			JOptionPane.showMessageDialog(this, "Error generating Excel report: " + ex.getMessage(), "Error",
					JOptionPane.ERROR_MESSAGE);
		}
	}
}
