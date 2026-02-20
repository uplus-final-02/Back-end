package core.security.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

	 @Override
	    public void commence(
	            HttpServletRequest request,
	            HttpServletResponse response,
	            AuthenticationException authException
	    ) throws IOException {

	        writeJson(response, HttpServletResponse.SC_UNAUTHORIZED,
	                "{\"code\":401,\"message\":\"인증이 필요합니다.\",\"data\":null}");
	    }

	    private void writeJson(HttpServletResponse response, int status, String json) throws IOException {
	        response.setStatus(status);
	        response.setCharacterEncoding("UTF-8");
	        response.setContentType("application/json;charset=UTF-8");
	        response.getWriter().write(json);
	    }
}
