package com.example.demogateway.exception;

/**
 * 
 * @author moyanxia
 *
 */
public class TokenAuthenticationException extends RuntimeException {
	private static final long serialVersionUID = 124457618734427436L;
	private final Integer code;

	public TokenAuthenticationException(Integer code, String message) {
		super(message);
		this.code = code;
	}

	public Integer getCode() {
		return code;
	}
}
