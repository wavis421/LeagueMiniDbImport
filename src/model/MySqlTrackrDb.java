package model;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.sql.PreparedStatement;
import com.mysql.jdbc.exceptions.jdbc4.CommunicationsException;
import com.mysql.jdbc.exceptions.jdbc4.MySQLNonTransientConnectionException;

/**
 * MySqlTrackrDb: MySql Student Tracker database access used only by Mini Tracker.
 * 
 * @author wavis
 *
 */
public class MySqlTrackrDb {
	MySqlDatabase trackrDb;

	public MySqlTrackrDb(MySqlDatabase trackrDb) {
		this.trackrDb = trackrDb;
	}

	public ArrayList<StudentModel> getActiveStudentsWithClass() {
		ArrayList<StudentModel> nameList = new ArrayList<StudentModel>();
		DateTime today = new DateTime().withZone(DateTimeZone.forID("America/Los_Angeles"));

		for (int i = 0; i < 2; i++) {
			try {
				// If Database no longer connected, the exception code will re-connect
				PreparedStatement selectStmt = trackrDb.dbConnection
						.prepareStatement("SELECT * FROM Students WHERE isInMasterDb AND CurrentClass != '' "
								+ "ORDER BY FirstName, LastName;");
				ResultSet result = selectStmt.executeQuery();

				while (result.next()) {	
					nameList.add(new StudentModel(result.getInt("ClientID"),
							new StudentNameModel(result.getString("FirstName"), result.getString("LastName"),
									result.getBoolean("isInMasterDb")),
							trackrDb.getAge(today, result.getString("Birthdate")), result.getString("GithubName"),
							result.getInt("Gender"), result.getDate("StartDate"), result.getInt("Location"),
							result.getInt("GradYear"), result.getString("CurrentClass"), result.getString("Email"),
							result.getString("AcctMgrEmail"), result.getString("EmergencyEmail"),
							result.getString("Phone"), result.getString("AcctMgrPhone"), result.getString("HomePhone"),
							result.getString("EmergencyPhone"), result.getString("CurrentModule"),
							result.getString("CurrentLevel"), result.getString("RegisterClass"),
							result.getDate("LastVisitDate")));
				}

				result.close();
				selectStmt.close();
				break;

			} catch (CommunicationsException | MySQLNonTransientConnectionException | NullPointerException e1) {
				if (i == 0) {
					// First attempt to re-connect
					trackrDb.connectDatabase();
				}

			} catch (SQLException e2) {
				MySqlDbLogging.insertLogData(LogDataModel.STUDENT_DB_ERROR, new StudentNameModel("", "", false), 0,
						": " + e2.getMessage());
				break;
			}
		}
		return nameList;
	}
}
