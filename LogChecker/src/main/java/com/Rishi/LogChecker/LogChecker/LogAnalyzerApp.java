package com.Rishi.LogChecker.LogChecker;

import java.awt.BorderLayout;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class LogAnalyzerApp extends JFrame {

	/**
	 * 
	 */
	private static final long serialVersionUID = 8305512038161872481L;

	public LogAnalyzerApp() {
		setTitle("Log Analyzer");
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		setSize(400, 200);

		JButton analyzeButton = new JButton("Analyze Log File");
		analyzeButton.addActionListener(e -> analyzeLogFile());

		JPanel buttonPanel = new JPanel();
		buttonPanel.add(analyzeButton);

		getContentPane().add(buttonPanel, BorderLayout.CENTER);
	}

	private void analyzeLogFile() {
		JFileChooser fileChooser = new JFileChooser();
		int returnValue = fileChooser.showOpenDialog(this);
		if (returnValue == JFileChooser.APPROVE_OPTION) {
			try {
				String filename = fileChooser.getSelectedFile().getAbsolutePath();
				Map<String, Integer> exceptionCounts = new HashMap<>();

				BufferedReader reader = new BufferedReader(new FileReader(filename));
				String line;
				StringBuilder currentException = new StringBuilder();
				boolean inException = false;

				while ((line = reader.readLine()) != null) {
					// Check for ERROR log level followed by date and time
					if (line.startsWith("ERROR ")) {
						// Start of a new exception
						if (inException) {
							// Process previous exception
							processException(currentException.toString(), exceptionCounts);
							currentException.setLength(0);
						}
						currentException.append(line).append("\n");
						inException = true;
					} else if (inException) {
						// Continue current exception
						currentException.append(line).append("\n");
					}
				}

				// Process the last exception, if any
				if (inException) {
					processException(currentException.toString(), exceptionCounts);
				}

				reader.close();

				// Generate Excel report
				generateExcelReport(exceptionCounts);

			} catch (IOException ex) {
				JOptionPane.showMessageDialog(this, "Error reading log file: " + ex.getMessage(), "Error",
						JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	private void processException(String exceptionText, Map<String, Integer> exceptionCounts) {
		StringBuilder fullException = new StringBuilder();
		String[] lines = exceptionText.split("\n");
		boolean startException = false;

		for (String line : lines) {
			if (startException) {
				if (line.trim().startsWith("at ")) {
					fullException.append(line.trim()).append("\n");
				} else if (line.trim().startsWith("Caused by:")) {
					fullException.append(line.trim()).append("\n");
				}
			} else if (line.startsWith("ERROR ")) {
				fullException.append(line).append("\n");
				startException = true;
			}
		}

		String message = fullException.toString().trim();
		exceptionCounts.put(message, exceptionCounts.getOrDefault(message, 0) + 1);
	}

	private void generateExcelReport(Map<String, Integer> exceptionCounts) {
		Workbook workbook = new XSSFWorkbook();
		Sheet sheet = workbook.createSheet("Exception Report");

		// Populate data
		int rowNum = 0;
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

		try (FileOutputStream outputStream = new FileOutputStream(reportFileName)) {
			// Write workbook to file
			workbook.write(outputStream);
			workbook.close();

			JOptionPane.showMessageDialog(this, "Excel report generated successfully: " + reportFileName,
					"Report Generated", JOptionPane.INFORMATION_MESSAGE);

		} catch (IOException ex) {
			JOptionPane.showMessageDialog(this, "Error generating Excel report: " + ex.getMessage(), "Error",
					JOptionPane.ERROR_MESSAGE);
		}
	}

}
