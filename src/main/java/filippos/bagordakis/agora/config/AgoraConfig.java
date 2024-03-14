package filippos.bagordakis.agora.config;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.Optional;
import java.util.Set;

import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.context.annotation.Import;

import filippos.bagordakis.agora.agora.Agora;
import filippos.bagordakis.agora.agora.AgoraDistributionHandler;
import filippos.bagordakis.agora.agora.data.event.AgoraEvent;
import filippos.bagordakis.agora.stoa.annotation.Dose;
import filippos.bagordakis.agora.stoa.annotation.Stoa;
import filippos.bagordakis.agora.stoa.enums.ResponseTypesEnum;
import filippos.bagordakis.agora.stoa.settings.StoaMethodSettings;
import filippos.bagordakis.agora.stoa.settings.StoaMethodSettings.Builder;
import filippos.bagordakis.agora.stoa.settings.StoaSettings;
import jakarta.annotation.PostConstruct;

@Import({ Agora.class, AgoraDistributionHandler.class })
public class AgoraConfig implements BeanFactoryPostProcessor, ApplicationEventPublisherAware {

	private static Logger log = LoggerFactory.getLogger(AgoraConfig.class);

	@Autowired
	private ApplicationEventPublisher applicationEventPublisher;

	@PostConstruct
	public void init() {
		log.info("Agora is now open");
	}

	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
		Reflections reflections = new Reflections("");
		Set<Class<?>> annotated = reflections.getTypesAnnotatedWith(Stoa.class);
		for (Class<?> proxyInterface : annotated) {
			if (!proxyInterface.isInterface()) {
				continue;
			}
			Class<?> iface = proxyInterface;
			Stoa stoaAnnotation = iface.getAnnotation(Stoa.class);
			String beanName = stoaAnnotation.value().equals("") ? proxyInterface.getSimpleName()
					: stoaAnnotation.value();
			beanName = Character.toLowerCase(beanName.charAt(0)) + beanName.substring(1);
			Object proxy = createProxyObject(iface, stoaAnnotation);
			beanFactory.registerSingleton(beanName, proxy);
		}
	}

	private StoaSettings extractSettings(Class<?> iface, Stoa stoaAnnotation) {
		StoaSettings requestClientSettings = new StoaSettings();
		for (Method method : iface.getMethods()) {
			if (method.isAnnotationPresent(Dose.class))
				requestClientSettings = extractDataFromMethod(requestClientSettings, method);
		}
		return requestClientSettings;
	}

	private StoaSettings extractDataFromMethod(StoaSettings stoaSettings, Method method) {
		ResponseTypesEnum type = ResponseTypesEnum.BODY;
		Class<?> returnType = method.getReturnType();
		Type responseType = method.getGenericReturnType();
		if (responseType instanceof ParameterizedType) {
			if (returnType == Optional.class) {
				type = ResponseTypesEnum.OPTIONAL;
			} else {
				throw new RuntimeException(returnType + " is not supported");
			}
			responseType = ((ParameterizedType) responseType).getActualTypeArguments()[0];
		}
		
		Dose doseAnnotation = method.getAnnotation(Dose.class);
		
		Class<?> responseClass = (Class<?>) responseType;
		Builder builder = new StoaMethodSettings.Builder(responseClass, type, doseAnnotation.value(), doseAnnotation.targets());

		stoaSettings.addMethodSettings(method, builder.build());
		return stoaSettings;
	}

	private Object createProxyObject(Class<?> iface, Stoa stoaAnnotation) {
		StoaSettings stoaSettings = extractSettings(iface, stoaAnnotation);
		log.info("Proxing {}", iface);
		return Proxy.newProxyInstance(iface.getClassLoader(), new Class[] { iface }, (proxyObj, method, args) -> {
			return proxyCode(proxyObj, method, args, stoaSettings.getMethodSettings(method));
		});
	}

	private Object proxyCode(Object proxyObj, Method method, Object[] args, StoaMethodSettings settings) {
		log.info("Dose got executed");
		Object toSend = args.length > 0 ? args[0] : null;
		applicationEventPublisher.publishEvent(new AgoraEvent(this, toSend, settings.getValue(), settings.getTargets()));		
		return null;
	}

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
		this.applicationEventPublisher = applicationEventPublisher;
	}

}
