package com.Rishi.LogChecker.LogChecker;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Log Analyzer
 *
 */
public class App {
	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> {
			try {
				// Use system look and feel
				UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			} catch (Exception ex) {
				ex.printStackTrace();
			}

			LogAnalyzerApp logAnalyzerApp = new LogAnalyzerApp();
			logAnalyzerApp.setVisible(true);
		});
	}
}
