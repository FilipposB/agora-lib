package filippos.bagordakis.agora.stoa.settings;

import java.util.Arrays;
import java.util.List;

import filippos.bagordakis.agora.stoa.enums.ResponseTypesEnum;

public class StoaMethodSettings {

	private final Class<?> returnType;
	private final ResponseTypesEnum responseTypesEnum;
	private final String value;
	private final List<String> targets;

	private StoaMethodSettings(Builder builder) {
		this.returnType = builder.returnType;
		this.responseTypesEnum = builder.type;
		this.value = builder.value;
		this.targets = Arrays.asList(builder.targets);
	}

	public Class<?> getReturnType() {
		return returnType;
	}

	public ResponseTypesEnum getResponseTypesEnum() {
		return responseTypesEnum;
	}

	public String getValue() {
		return value;
	}

	public List<String> getTargets() {
		return targets;
	}

	public static class Builder {
		private final Class<?> returnType;
		private final ResponseTypesEnum type;
		private final String value;
		private final String[] targets;

		public Builder(Class<?> returnType, ResponseTypesEnum type, String value, String[] targets) {
			this.returnType = returnType;
			this.type = type;
			this.value = value;
			this.targets = targets;
		}

		public StoaMethodSettings build() {
			return new StoaMethodSettings(this);
		}

	}
}
