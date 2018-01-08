package org.nalby.yobatis.sql.mysql;

import static org.hamcrest.Matchers.nullValue;

import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.nalby.yobatis.exception.ProjectException;
import org.nalby.yobatis.exception.SqlConfigIncompleteException;
import org.nalby.yobatis.sql.Sql;
import org.nalby.yobatis.sql.Table;
import org.nalby.yobatis.util.Expect;

public class Mysql extends Sql {
	
	
	private String timedoutUrl;

	private Mysql(String username, String password, String url, String connectorJarPath, String driverClassName) {
		try {
			this.username = username;
			this.password = password;
			this.url = url;
			this.connectorJarPath = connectorJarPath;
			this.driverClassName = driverClassName;
			URL jarurl = new URL("file://" + connectorJarPath);
			URLClassLoader classLoader = new URLClassLoader(new URL[] { jarurl });
			Driver driver = (Driver) Class.forName(driverClassName, true, classLoader).newInstance();
			DriverWrapper driverWrapper = new DriverWrapper(driver);
			DriverManager.registerDriver(driverWrapper);
			timedoutUrl = this.url;
			if (!this.timedoutUrl.contains("socketTimeout")) {
				if (this.timedoutUrl.contains("?")) {
					this.timedoutUrl = this.timedoutUrl + "&socketTimeout=2000";
				} else {
					this.timedoutUrl = this.timedoutUrl + "?socketTimeout=2000";
				}
			}
			if (!this.timedoutUrl.contains("connectTimeout")) {
				this.timedoutUrl = this.timedoutUrl + "&connectTimeout=2000";
			}
		} catch (Exception e) {
			throw new ProjectException(e);
		}
	}
	
	private Connection getConnection() throws SQLException {
		return DriverManager.getConnection(this.timedoutUrl, username, password);
	}
	
	private Table makeTable(String name) {
		Table table = new Table(name);
		try (Connection connection = getConnection()) {
			DatabaseMetaData metaData = connection.getMetaData();
			ResultSet resultSet = metaData.getColumns(null, null, name, null);
			while (resultSet.next()) {
				String columnName = resultSet.getString("COLUMN_NAME");
				String autoIncrment = resultSet.getString("IS_AUTOINCREMENT");
				if (autoIncrment.toLowerCase().contains("true") ||
					autoIncrment.toLowerCase().contains("yes")) {
					table.addAutoIncColumn(columnName);
				}
			}
			resultSet.close();
			resultSet = metaData.getPrimaryKeys(null,null, name);
			while(resultSet.next()) {
				table.addPrimaryKey(resultSet.getString("COLUMN_NAME"));
			}
			resultSet.close();
		} catch (Exception e) {
			//Nothing we can do.
		}
		return table;
	}
	
	/**
	 * Get all tables according to the configuration.
	 * @return tables.
	 * @throws ProjectException, if any configuration value is not valid.
	 */
	@Override
	public List<Table> getTables() {
		List<Table> result = new ArrayList<>();
		try (Connection connection = getConnection()) {
			DatabaseMetaData meta = connection.getMetaData();
			ResultSet res = meta.getTables(null, null, null, new String[] {"TABLE"});
			while (res.next()) {
				String tmp = res.getString("TABLE_NAME");
				result.add(makeTable(tmp));
			}
			res.close();
			return result;
		} catch (Exception e) {
			throw new ProjectException(e);
		}
	}
	
	
	public static class Builder {
		private String username;
		private String password;
		private String url;
		private String connectorJarPath;
		private String driverClassName;
		private Builder(){}
		public Builder setUsername(String username) {
			this.username = username;
			return this;
		}
		public Builder setPassword(String password) {
			this.password = password;
			return this;
		}
		public Builder setUrl(String url) {
			this.url = url;
			return this;
		}
		public Builder setConnectorJarPath(String connectorJarPath) {
			this.connectorJarPath = connectorJarPath;
			return this;
		}
		public Builder setDriverClassName(String driverClassName) {
			this.driverClassName = driverClassName;
			return this;
		}
		public Mysql build() {
			Expect.notEmpty(username, "username must not be null.");
			Expect.notEmpty(password, "password must not be null.");
			Expect.notEmpty(url, "url must not be null.");
			Expect.notEmpty(connectorJarPath, "connectorJarPath must not be null.");
			Expect.notEmpty(driverClassName, "driverClassName must not be null.");
			return new Mysql(username, password, url, connectorJarPath, driverClassName);
		}
	}
	
	public static Builder builder() {
		return new Builder();
	}
	
	@Override
	public String getSchema() {
		try {
			Pattern pattern = Pattern.compile("jdbc:mysql://[^/]+/([^?]+).*");
			Matcher matcher = pattern.matcher(url);
			return matcher.find() ? matcher.group(1) : null;
		} catch (SqlConfigIncompleteException e) {
			return null;
		}
	}
	
	// See http://www.kfu.com/~nsayer/Java/dyn-jdbc.html
	private static class DriverWrapper implements Driver {
		
		private Driver driver;

		public DriverWrapper(Driver driver) {
			this.driver = driver;
		}

		@Override
		public Connection connect(final String url, Properties info)
				throws SQLException {
			return driver.connect(url, info);
		}

		@Override
		public boolean acceptsURL(String url) throws SQLException {
			return driver.acceptsURL(url);
		}

		@Override
		public DriverPropertyInfo[] getPropertyInfo(String url, Properties info)
				throws SQLException {
			return driver.getPropertyInfo(url, info);
		}

		@Override
		public int getMajorVersion() {
			return driver.getMajorVersion();
		}

		@Override
		public int getMinorVersion() {
			return driver.getMinorVersion();
		}

		@Override
		public boolean jdbcCompliant() {
			return driver.jdbcCompliant();
		}

		@Override
		public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
			return driver.getParentLogger();
		}
		
	}
}
