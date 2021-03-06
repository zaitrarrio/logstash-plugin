package jenkins.plugins.logstash.persistence;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.PrintStream;
import java.net.SocketException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.rabbitmq.client.AuthenticationFailureException;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

@RunWith(MockitoJUnitRunner.class)
public class RabbitMqDaoTest {
  RabbitMqDao dao;
  @Mock ConnectionFactory mockPool;
  @Mock Connection mockConnection;
  @Mock Channel mockChannel;
  @Mock PrintStream mockLogger;

  @Before
  public void before() throws Exception {
    int port = (int) (Math.random() * 1000);
    dao = new RabbitMqDao("localhost", port, "logstash", "username", "password");

    // Note that we can't run these tests in parallel
    dao.pool = mockPool;

    when(mockPool.newConnection()).thenReturn(mockConnection);

    when(mockConnection.createChannel()).thenReturn(mockChannel);
  }

  @After
  public void after() throws Exception {
    verifyNoMoreInteractions(mockPool);
    verifyNoMoreInteractions(mockConnection);
    verifyNoMoreInteractions(mockChannel);
    verifyNoMoreInteractions(mockLogger);
  }

  @Test(expected = IllegalArgumentException.class)
  public void constructorFailNullHost() throws Exception {
    try {
      new RabbitMqDao(null, 5672, "logstash", "username", "password");
    } catch (IllegalArgumentException e) {
      assertEquals("Wrong error message was thrown", "host name is required", e.getMessage());
      throw e;
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void constructorFailEmptyHost() throws Exception {
    try {
      new RabbitMqDao(" ", 5672, "logstash", "username", "password");
    } catch (IllegalArgumentException e) {
      assertEquals("Wrong error message was thrown", "host name is required", e.getMessage());
      throw e;
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void constructorFailNullKey() throws Exception {
    try {
      new RabbitMqDao("localhost", 5672, null, "username", "password");
    } catch (IllegalArgumentException e) {
      assertEquals("Wrong error message was thrown", "rabbit queue name is required", e.getMessage());
      throw e;
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void constructorFailEmptyKey() throws Exception {
    try {
      new RabbitMqDao("localhost", 5672, " ", "username", "password");
    } catch (IllegalArgumentException e) {
      assertEquals("Wrong error message was thrown", "rabbit queue name is required", e.getMessage());
      throw e;
    }
  }

  @Test
  public void constructorSuccess() throws Exception {
    // Unit under test
    dao = new RabbitMqDao("localhost", 5672, "logstash", "username", "password");

    // Verify results
    assertEquals("Wrong host name", "localhost", dao.host);
    assertEquals("Wrong port", 5672, dao.port);
    assertEquals("Wrong key", "logstash", dao.key);
    assertEquals("Wrong password", "password", dao.password);
  }

  @Test
  public void pushFailUnauthorized() throws Exception {
    // Initialize mocks
    when(mockPool.newConnection()).thenThrow(new AuthenticationFailureException("Not authorized"));

    // Unit under test
    long result = dao.push("", mockLogger);

    // Verify results
    assertEquals("Return code should be an error", -1L, result);

    verify(mockPool).newConnection();
    verify(mockLogger).println(Matchers.startsWith("com.rabbitmq.client.AuthenticationFailureException: Not authorized"));
  }

  @Test
  public void pushFailCantConnect() throws Exception {
    // Initialize mocks
    when(mockPool.newConnection()).thenThrow(new SocketException("Connection refused"));

    // Unit under test
    long result = dao.push("", mockLogger);

    // Verify results
    assertEquals("Return code should be an error", -1L, result);

    verify(mockPool).newConnection();
    verify(mockLogger).println(Matchers.startsWith("java.net.SocketException: Connection refused"));
  }

  @Test
  public void pushFailCantWrite() throws Exception {
    // Initialize mocks
    doThrow(new SocketException("Queue length limit exceeded")).when(mockChannel).basicPublish("", "logstash", null, "{}".getBytes());

    // Unit under test
    long result = dao.push("{}", mockLogger);

    // Verify results
    assertEquals("Return code should be an error", -1L, result);

    verify(mockPool).newConnection();
    verify(mockConnection).createChannel();
    verify(mockChannel).queueDeclare("logstash", true, false, false, null);
    verify(mockChannel).basicPublish("", "logstash", null, "{}".getBytes());
    verify(mockLogger).println(Matchers.startsWith("java.net.SocketException: Queue length limit exceeded"));
  }

  @Test
  public void pushSuccess() throws Exception {
    String json = "{ 'foo': 'bar' }";

    // Unit under test
    long result = dao.push(json, mockLogger);

    // Verify results
    assertEquals("Unexpected return code", 1L, result);

    verify(mockPool).newConnection();
    verify(mockConnection).createChannel();
    verify(mockConnection).close();
    verify(mockChannel).queueDeclare("logstash", true, false, false, null);
    verify(mockChannel).basicPublish("", "logstash", null, json.getBytes());
    verify(mockChannel).close();
  }

  @Test
  public void pushSuccessNoAuth() throws Exception {
    String json = "{ 'foo': 'bar' }";
    dao = new RabbitMqDao("localhost", 5672, "logstash", null, null);
    dao.pool = mockPool;

    // Unit under test
    long result = dao.push(json, mockLogger);

    // Verify results
    assertEquals("Unexpected return code", 1L, result);

    verify(mockPool).newConnection();
    verify(mockConnection).createChannel();
    verify(mockConnection).close();
    verify(mockChannel).queueDeclare("logstash", true, false, false, null);
    verify(mockChannel).basicPublish("", "logstash", null, json.getBytes());
    verify(mockChannel).close();
  }
}
