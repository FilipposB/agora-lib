package filippos.bagordakis.agora.agora.data.event;

import java.util.List;

import org.springframework.context.ApplicationEvent;

import filippos.bagordakis.agora.common.dto.RequestDTO;
import filippos.bagordakis.agora.common.helper.AgoraHelper;

public class AgoraEvent extends ApplicationEvent {
	private static final long serialVersionUID = -9018409911630405844L;

	private final RequestDTO requestDTO;

	public AgoraEvent(Object source, Object data, String keyword, List<String> targets) {
		super(source);
		this.requestDTO = AgoraHelper.objectToRequestDTO(data, keyword, targets);
	}

	public RequestDTO getData() {
		return requestDTO;
	}

}
