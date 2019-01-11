package controller;

import java.util.ArrayList;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import model.LocationLookup;
import model.MiniTrackerDatabase;
import model.MySqlDatabase;
import model.MySqlDbLogging;
import model.MySqlTrackrDb;
import model.StudentModel;

public class MiniDataImport {
	private static final String DATABASE = "MiniTracker";
	private static final String DB_USER = "Importer";

	private Session session = null;

	public static void main(String[] args) {
		new MiniDataImport();
	}

	public MiniDataImport() {
		// Connect to Mini Tracker database
		MiniTrackerDatabase miniDb = new MiniTrackerDatabase();
		session = miniDb.connectToServer();
		if (session == null) {
			System.out.println("Failed to connect to Mini Tracker database");
			System.exit(0);
		}
		System.out.println("Successfully connected to mini trackr database");

		// Connect to Student Tracker database
		MySqlDatabase trackrDb = new MySqlDatabase("Put PW Here", MySqlDatabase.MINI_TRACKER_SSH_PORT);
		if (!trackrDb.connectDatabase()) {
			System.out.println("Failed to connect to Tracker database");
			System.exit(0);
		}
		new MySqlDbLogging(trackrDb);
		LocationLookup.setLocationData(trackrDb.getLocationList());
		MySqlTrackrDb localTrackrDb = new MySqlTrackrDb(trackrDb); 
		System.out.println("Successfully connected to tracker database");

		ArrayList<StudentModel> trackerList = localTrackrDb.getActiveStudentsWithClass();
		removeAllStudents();
		for (StudentModel s : trackerList) {
			insertStudent(s);
		}
		System.out.println("Tracker List: " + trackerList.size());

		trackrDb.disconnectDatabase();
		miniDb.closeConnection();
	}

	private void removeAllStudents() {
		try {
			ChannelExec execChannel = (ChannelExec) session.openChannel("exec");
			execChannel.setCommand("mysql -u " + DB_USER + " -e \"DELETE FROM Students\" " + DATABASE);
			execChannel.connect();
			waitForCommandExecution(execChannel, "DELETE");
			execChannel.disconnect();

		} catch (JSchException e) {
			System.out.println("MySql command failed (delete): " + e.getMessage());
		}
	}

	private void insertStudent(StudentModel s) {
		try {
			ChannelExec execChannel = (ChannelExec) session.openChannel("exec");

			String firstName = s.getFirstName().replace("'", "\\'");
			if (s.getGithubName() == null || s.getGithubName().equalsIgnoreCase("null") || s.getGithubName().trim().equals("")) {
				execChannel.setCommand("mysql -u " + DB_USER
						+ " -e \"INSERT INTO Students (ClientID, FirstName, LastInitial, "
						+ "HomeLocCode, HomeLocation, CurrentClass, CurrentLevel) VALUES (" + s.getClientID() + ", '" 
						+ firstName + "', '" + s.getLastName().substring(0, 1) + "', '"
						+ LocationLookup.getLocationCodeString(s.getHomeLocation()) + "', '"
						+ LocationLookup.convertLocationToString(s.getHomeLocation()) + "', '" + s.getCurrentClass() + "', '"
						+ s.getCurrentLevel() + "')\" " + DATABASE);
			} else {
				execChannel.setCommand("mysql -u " + DB_USER
						+ " -e \"INSERT INTO Students (ClientID, FirstName, LastInitial, GithubName, "
						+ "HomeLocCode, HomeLocation, CurrentClass, CurrentLevel) VALUES (" + s.getClientID() + ", '" 
						+ firstName + "', '" + s.getLastName().substring(0, 1) + "', '" + s.getGithubName().trim() + "', '"
						+ LocationLookup.getLocationCodeString(s.getHomeLocation()) + "', '"
						+ LocationLookup.convertLocationToString(s.getHomeLocation()) + "', '" + s.getCurrentClass() + "', '"
						+ s.getCurrentLevel() + "')\" " + DATABASE);
			}
			execChannel.connect();

			waitForCommandExecution(execChannel, "INSERT");

			int res = execChannel.getExitStatus();
			if (res != 0)
				System.out.println("Result: " + execChannel.getExitStatus() + ", " + s.getClientID() + ", "
						+ s.getFirstName() + " " + s.getLastName() + ", " + s.getGithubName() + ", "
						+ LocationLookup.convertLocationToString(s.getHomeLocation()) + " ("
						+ LocationLookup.getLocationCodeString(s.getHomeLocation()) + "), " + s.getCurrentClass() + " ("
						+ s.getCurrentLevel() + ")");
			execChannel.disconnect();

		} catch (JSchException e) {
			System.out.println("MySql command failed (insert): " + e.getMessage());
		}
	}

	private void waitForCommandExecution(ChannelExec execChannel, String cmdString) {
		int sleepCount = 0;
		// Sleep for up to 10 seconds while the command executes
		do {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				System.out.println("Interrupted exception while waiting for command to finish: " + e.getMessage());
			}
		} while (!execChannel.isClosed() && sleepCount++ < 100);

		if (sleepCount >= 100)
			System.out.println(cmdString + " command timed out");
	}
}
