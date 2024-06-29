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

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class LogAnalyzerApp extends JFrame {

	private static final long serialVersionUID = 824575624004827705L;
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
		logPatternField = new JTextField("^(ERROR) .*", 40);

		JLabel timestampPatternLabel = new JLabel("Timestamp Pattern (Regex):");
		timestampPatternField = new JTextField(
				"ERROR \\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2},\\d{3} \\[Webcontainer:\\d+\\]", 60);

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
				+ "   - Default log pattern: ^(ERROR) .* \n"
				+ "   - Default timestamp pattern: ERROR \\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2},\\d{3} \\[Webcontainer:\\d+\\]\n"
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

				BufferedReader reader = new BufferedReader(new FileReader(filename));
				String line;
				StringBuilder currentException = new StringBuilder();
				boolean inException = false;

				// Count total lines for progress calculation
				int totalLines = 0;
				while (reader.readLine() != null) {
					totalLines++;
				}
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
							processException(currentException.toString(), exceptionCounts,
									timestampPatternField.getText());
							currentException.setLength(0);
						}
						currentException.append(line).append("\n");
						inException = true;
					} else if (line.startsWith("DEBUG") || line.startsWith("WARN") || line.startsWith("INFO")
							|| line.startsWith("TRACE")) {
						// Ignore non-ERROR logs
						inException = false;
					} else if (inException) {
						// Continue current exception
						currentException.append(line).append("\n");
					}
				}

				// Process the last exception, if any
				if (inException) {
					processException(currentException.toString(), exceptionCounts, timestampPatternField.getText());
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

	private void processException(String exceptionText, Map<String, Integer> exceptionCounts, String timestampPattern) {
		Pattern pattern = Pattern.compile(timestampPattern);
		Matcher matcher = pattern.matcher(exceptionText);
		String logWithoutTimestamp = matcher.replaceAll("ERROR").trim();
		exceptionCounts.put(logWithoutTimestamp, exceptionCounts.getOrDefault(logWithoutTimestamp, 0) + 1);
	}

	private void generateExcelReport(Map<String, Integer> exceptionCounts) {
		Workbook workbook = new XSSFWorkbook();
		Sheet sheet = workbook.createSheet("Exception Report");

		// Create header row
		Row headerRow = sheet.createRow(0);
		headerRow.createCell(0).setCellValue("Exception");
		headerRow.createCell(1).setCellValue("Count");

		// Populate data rows
		int rowNum = 1;
		for (Map.Entry<String, Integer> entry : exceptionCounts.entrySet()) {
			Row row = sheet.createRow(rowNum++);
			row.createCell(0).setCellValue(entry.getKey());
			row.createCell(1).setCellValue(entry.getValue());
		}

		// Save the Excel file
		try {
			Path logsDir = Paths.get(System.getProperty("user.home"), "Desktop", "logs");
			Files.createDirectories(logsDir);
			String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
			Path excelFile = logsDir.resolve("ExceptionReport_" + timestamp + ".xlsx");
			FileOutputStream fileOut = new FileOutputStream(excelFile.toFile());
			workbook.write(fileOut);
			fileOut.close();
			workbook.close();
			JOptionPane.showMessageDialog(this, "Excel report generated: " + excelFile.toString(), "Success",
					JOptionPane.INFORMATION_MESSAGE);
		} catch (IOException e) {
			JOptionPane.showMessageDialog(this, "Error saving Excel report: " + e.getMessage(), "Error",
					JOptionPane.ERROR_MESSAGE);
		}
	}

//	public static void main(String[] args) {
//		SwingUtilities.invokeLater(() -> {
//			LogAnalyzerApp app = new LogAnalyzerApp();
//			app.setVisible(true);
//		});
//	}
}
