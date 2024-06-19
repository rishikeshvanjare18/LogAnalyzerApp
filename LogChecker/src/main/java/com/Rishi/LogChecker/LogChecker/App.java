package com.Rishi.LogChecker.LogChecker;

import javax.swing.SwingUtilities;

/**
 * Log Analyzer
 *
 */
public class App {
	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> {
			LogAnalyzerApp app = new LogAnalyzerApp();
			app.setVisible(true);
		});
	}
}
