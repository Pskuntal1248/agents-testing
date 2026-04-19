import java.sql.*;
public class SQLServerDB {
    public static void main(String[] args) {
        String url = "jdbc:sqlserver://localhost:1433;databaseName=CompanyDB;encrypt=true;trustServerCertificate=true;";
        try {
            Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
            Connection conn = DriverManager.getConnection(url, "sa", "password");
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT TOP 1 ProductName FROM Products");
            
            System.out.println("--- SQL Server Output ---");
            if(rs.next()) {
                System.out.println("Product: " + rs.getString("ProductName"));
            }
            conn.close();
        } catch (Exception e) { e.printStackTrace(); }
    }
}