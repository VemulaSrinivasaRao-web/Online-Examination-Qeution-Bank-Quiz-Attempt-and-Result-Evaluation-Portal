package com.example.exam.exception;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletRequest;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleMaxSizeException(MaxUploadSizeExceededException exc, 
                                          HttpServletRequest request,
                                          RedirectAttributes redirectAttributes) {
        
        String referer = request.getHeader("Referer");
        redirectAttributes.addFlashAttribute("errorMessage", "Maximum upload size exceeded! Please upload a file smaller than 10MB.");
        
        if (referer != null) {
            return "redirect:" + referer;
        }
        return "redirect:/";
    }
}
