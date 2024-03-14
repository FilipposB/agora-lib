package filippos.bagordakis.agora.agora;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import filippos.bagordakis.agora.common.dto.RequestDTO;
import filippos.bagordakis.agora.stoa.annotation.Agora;
import filippos.bagordakis.agora.stoa.annotation.Pare;
import filippos.bagordakis.agora.stoa.settings.PareInfo;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

public class AgoraDistributionHandler {

	private static final Logger log = LoggerFactory.getLogger(AgoraDistributionHandler.class);

	private final ApplicationContext applicationContext;
	private final ExecutorService executor;
	private final Map<String, PareInfo> agores;
	private boolean started = false;

	public AgoraDistributionHandler(ApplicationContext applicationContext) {
		this.executor = Executors.newWorkStealingPool();
		this.applicationContext = applicationContext;
		agores = wireUpPare();
	}

	@PostConstruct
	public void start() {
		started = true;
	}

	private Map<String, PareInfo> wireUpPare() {
		Map<String, PareInfo> valuesToMethods = new ConcurrentHashMap<>();


		String[] beanNames = applicationContext.getBeanNamesForAnnotation(Agora.class);
		for (String beanName : beanNames) {

			Object bean = applicationContext.getBean(beanName);
			Class<?> beanClass = bean.getClass();

			for (Method method : beanClass.getDeclaredMethods()) {
				if (method.isAnnotationPresent(Pare.class)) {
					Pare pare = method.getAnnotation(Pare.class);
					String value = pare.value();

					if (valuesToMethods.containsKey(value)) {
						throw new RuntimeException("Duplicate value for @Pare annotation found: " + value);
					}

					Class<?> firstParamType = null;
					if (method.getParameterCount() > 0) {
						firstParamType = method.getParameterTypes()[0];
					}

					PareInfo info = new PareInfo(bean, method, firstParamType);

					valuesToMethods.put(value, info);
				}
			}
			log.info(valuesToMethods.toString());
		}

		return valuesToMethods;

	}

	@PreDestroy
	public void stop() {
		if (started) {
			executor.shutdown();
		}
	}

	public void feedQue(RequestDTO requestDTO) {
		this.executor.submit(new Task(requestDTO));
	}

	private class Task implements Runnable {

		private final RequestDTO task;

		public Task(RequestDTO task) {
			this.task = task;
		}

		@Override
		public void run() {
			try {
				agores.get(task.getKeyword()).execute(task.getJsonData());
			} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				e.printStackTrace();
			}
		}

	}

}
