package com.erp.common.filter;

import java.io.IOException;
import java.net.URLDecoder;

import javax.servlet.*;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

import com.erp.auth.repository.AuthRepository;
import com.erp.common.rest.RestBusinessException;
import com.erp.common.rest.RestBusinessException.StatusCode;
import com.erp.common.security.SecurityContext;
import com.erp.common.security.UserInfo;
import com.erp.common.util.AES256Util;
import com.fasterxml.jackson.databind.ObjectMapper;


public class AuthFilter implements Filter {
	private static final ObjectMapper om = new ObjectMapper();
	private static final AuthRepository authRepository = new AuthRepository(); 

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		HttpServletRequest httpRequest = (HttpServletRequest) request;
		String uri = httpRequest.getRequestURI();
		String contextPath = httpRequest.getContextPath();
		// 정적 리소스 요청은 필터 제외
		if (uri.startsWith(contextPath + "/erp/css") || uri.startsWith(contextPath + "/erp/js")
				|| uri.startsWith(contextPath + "/erp/images")) {
			chain.doFilter(request, response);
			return;
		}

		setUserInfoByCookie(httpRequest.getCookies());
		checkAuth(httpRequest.getRequestURI(), httpRequest.getMethod());
		chain.doFilter(request, response);
		SecurityContext.clear();
	}

	private void setUserInfoByCookie(Cookie[] cookies) {
		try {
			boolean findCookie = false;
			if (cookies == null)
				setGuestUserInfo();
			else {
				for (Cookie cookie : cookies) {
					if (cookie.getName().equals("auth")) {
						SecurityContext.setCurrentUser(om.reader().readValue(
								AES256Util.decrypt(URLDecoder.decode(cookie.getValue(), "UTF-8")), UserInfo.class));
						findCookie = true;
						break;
					}
				}
			}
			if (!findCookie)
				setGuestUserInfo();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void setGuestUserInfo() {
		SecurityContext.setCurrentUser(UserInfo.builder().userSeq(-1).roles(new int[] { -1 }).build());
	}
	
	private void checkAuth(String url, String methodName) {
		int roleSeq = authRepository.getRoleSeq(url, methodName);
		boolean check = true;
		for(int role : SecurityContext.getCurrentUser().getRoles()) {
			if(role == roleSeq) check = false;
		}
		if(check) throw new RestBusinessException(StatusCode.FORBIDDEN);
	}
}