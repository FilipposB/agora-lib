package filippos.bagordakis.agora.agora;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import filippos.bagordakis.agora.agora.data.event.AgoraEvent;
import filippos.bagordakis.agora.common.dto.AcknowledgmentDTO;
import filippos.bagordakis.agora.common.dto.BaseDTO;
import filippos.bagordakis.agora.common.dto.GreetingDTO;
import filippos.bagordakis.agora.common.dto.HeartbeatDTO;
import filippos.bagordakis.agora.common.dto.RequestDTO;
import filippos.bagordakis.agora.common.request.cache.AgoraRequestCache;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Component
public class Agora {

	@Value("${agora.ip:localhost}")
	private String SERVER_ADDRESS;
	@Value("${agora.port:12345}")
	private int SERVER_PORT;
	@Value("1000")
	private int RECONNECT_DELAY;

	private Socket socket = null;

	private ObjectOutputStream out = null;
	private ObjectInputStream in = null;

	private final long HEARTBEAT_INTERVAL = 1000;
	private final long HEARTBEAT_TIMEOUT = 10 * HEARTBEAT_INTERVAL;
	private long receivedHeartbeatTime = System.currentTimeMillis();

	private boolean running;
	private boolean connected;

	private static final Logger log = LoggerFactory.getLogger(Agora.class);

	private static ConcurrentLinkedQueue<BaseDTO> que;

	private static boolean shouldGreet = true;

	private static final AgoraRequestCache cache = new AgoraRequestCache(Duration.ofMillis(2000), x -> {
		if (x instanceof RequestDTO dto) {
			log.info("Didnt hear back will reque !");
			que.add(dto);
		} else if (x instanceof GreetingDTO dto) {
			log.info("Didnt hear back will greet again !");
			shouldGreet = true;
		}
	});

	@Value("${agora.id:Athens}")
	private String id;

	private final AgoraDistributionHandler agoraDistributionHandler;

	public Agora(AgoraDistributionHandler agoraDistributionHandler) {
		this.agoraDistributionHandler = agoraDistributionHandler;
		que = new ConcurrentLinkedQueue<>();
	}

	@EventListener
	protected void addToQue(AgoraEvent agoraEvent) {
		que.add(agoraEvent.getData());
	}

	@PostConstruct
	public void connect() throws InterruptedException {
		running = true;

		log.atInfo();

		new Thread(() -> {
			establishConnection();
			new Thread(new Listener()).start();
			new Thread(new Writer()).start();
		}).start();

	}

	@PreDestroy
	public void close() {

		running = false;

		closeConnections();
	}

	private synchronized void closeConnections() {
		connected = false;
		while (true) {
			try {
				if (socket != null && socket.isConnected()) {
					socket.close();
					log.info("Closing connection to Agora");
					socket = null;
				}
				if (out != null) {
					out = null;
				}
				if (in != null) {
					in = null;
				}

				break;
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private synchronized void establishConnection() {
		while (running) {
			try {
				closeConnections();
				acquireSocket();
				connectPrintWriter();
				getBufferedReader();
				receivedHeartbeatTime = System.currentTimeMillis();
				shouldGreet = true;
				connected = true;
				break;
			} catch (IOException e) {
				log.error("Failed to connect due to {}", e.getMessage());
				e.printStackTrace();
			}
		}
	}

	private void connectPrintWriter() throws IOException {

		out = new ObjectOutputStream(socket.getOutputStream());

	}

	private void getBufferedReader() throws IOException {

		in = new ObjectInputStream(socket.getInputStream());

	}

	private void acquireSocket() {
		while (running) {
			try {

				socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
				if (socket.isConnected()) {
					log.info("Connected to Agora at  [{}]:[{}]", SERVER_ADDRESS, SERVER_PORT);
					break;
				}
			} catch (UnknownHostException e) {
				log.error("Unkown host  {}", e.getMessage());
				throw new RuntimeException(e);
			} catch (IOException e) {
				log.error("Failed to connect to Agora at [{}]:[{}] with message : {}", SERVER_ADDRESS, SERVER_PORT,
						e.getMessage());
				try {
					Thread.sleep(RECONNECT_DELAY);
				} catch (InterruptedException e2) {
					throw new RuntimeException(e2);
				}
			}
		}
	}

	private class Listener implements Runnable {

		@Override
		public void run() {

			log.info("Agora Listener started");

			while (running) {

				while (connected) {

					BaseDTO dto;
					try {
						while ((dto = (BaseDTO) in.readObject()) != null) {

							boolean sendAcknoledgment = false;

							receivedHeartbeatTime = System.currentTimeMillis();

							if (dto instanceof HeartbeatDTO) {
								log.debug("Heartbeat received");
							} else if (dto instanceof AcknowledgmentDTO acknowledgmentDTO) {
								BaseDTO baseDTO = cache.remove(acknowledgmentDTO);

								if (baseDTO != null) {
									log.debug("Received acknoledgement for {}", baseDTO.toString());
								}

							} else if (dto instanceof RequestDTO requestDTO) {
								log.debug("Received object [{}] over TCP", dto.toString());
								agoraDistributionHandler.feedQue(requestDTO);
								sendAcknoledgment = true;
							}

							if (sendAcknoledgment) {
								que.add(new AcknowledgmentDTO(dto.getId()));
							}
						}
					} catch (IOException e) {
						log.error("{}", e.getMessage());
						e.printStackTrace();
						establishConnection();
					} catch (ClassNotFoundException e) {
						log.error("{}", e.getMessage());
						e.printStackTrace();

					}
				}

				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}

		}

	}
	
	

	private class Writer implements Runnable {

		private long lastHeartbeatSent = System.currentTimeMillis();

		@Override
		public void run() {

			log.info("Agora Writer started");

			while (running) {
				while (connected) {

					if (!socket.isConnected() || socket.isClosed()) {
						running = false;
						log.error("Socket is not connected or closed. Stopping sending data.");
						break;
					}

					try {

						if (shouldGreet) {
							GreetingDTO dto = new GreetingDTO(UUID.randomUUID(), id);
							out.writeObject(dto);
							lastHeartbeatSent = System.currentTimeMillis();
							cache.put(dto);
							shouldGreet = false;
						} else {
							BaseDTO requestDTO = que.poll();
							if (requestDTO != null) {
								out.writeObject(requestDTO);
								lastHeartbeatSent = System.currentTimeMillis();
								cache.put(requestDTO);
								log.debug("Sent object [{}] over TCP", requestDTO.toString());
							}
						}

						if (System.currentTimeMillis() - lastHeartbeatSent >= HEARTBEAT_INTERVAL) {
							HeartbeatDTO heartbeatDTO = HeartbeatDTO.newInstance();
							out.writeObject(heartbeatDTO);
							lastHeartbeatSent = System.currentTimeMillis();
							log.debug("Heartbeat [{}] sent to [{}]", heartbeatDTO.getId(),
									socket.getRemoteSocketAddress().toString());
						}

					} catch (IOException e) {
						log.error("Failed to serialize and send object", e);
					}

					if (System.currentTimeMillis() - receivedHeartbeatTime > HEARTBEAT_TIMEOUT) {
						establishConnection();
					}
				}

				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}

			}
		}
	}

}
