package com.zhouw.learn.scala.jdbc

import java.sql.{Connection, DriverManager, PreparedStatement, ResultSet}

object JdbcTest {

  val MySqlDriver = "com.mysql.jdbc.Driver"


  def getConnection(jdbcDriver: String, url: String, userName: String = "root", password: String): Connection = {
    try {
      Class.forName(jdbcDriver)
      DriverManager.getConnection(url, userName, password)
    } catch {
      case e: ClassNotFoundException => throw e
    }
  }

  val connection = (getConnection _).curried

  val getMySqlConnection = connection(MySqlDriver)(_: String)(_: String)(_: String)


  def main(args: Array[String]): Unit = {
    val driver = "com.mysql.jdbc.Driver"
    val url = "jdbc:mysql://localhost:3306/scala_test"
    val username = "root"
    val password = "123456"
    //    var connection = getConnection(driver,url,username,password)
    var mysqlConnection = getMySqlConnection(url, username, password)
    var ps = mysqlConnection.createStatement()
    val rs: ResultSet = ps.executeQuery("select * from sys_user")
    while (rs.next()) {
      println(rs.getString("name"))
    }
  }

}
