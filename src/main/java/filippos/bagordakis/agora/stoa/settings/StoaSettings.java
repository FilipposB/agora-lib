package filippos.bagordakis.agora.stoa.settings;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;


public class StoaSettings {

	private final Map<Method, StoaMethodSettings> methodUrlMap;

	public StoaSettings() {
		this.methodUrlMap = new HashMap<>();
	}

	public void addMethodSettings(Method method, StoaMethodSettings methodSettings) {
		methodUrlMap.put(method, methodSettings);
	}

	public StoaMethodSettings getMethodSettings(Method method) {
		return methodUrlMap.get(method);
	}
	
}

