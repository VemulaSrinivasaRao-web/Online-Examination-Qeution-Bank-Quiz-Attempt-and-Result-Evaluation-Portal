package com.example.exam.controller;

import com.example.exam.model.User;
import com.example.exam.service.FileStorageService;
import com.example.exam.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.GrantedAuthority;

@Controller
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private FileStorageService fileStorageService;

    @GetMapping("/")
    public String home() {
        // Get the current user's authentication details
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated()
                && !(authentication.getPrincipal() instanceof String
                        && authentication.getPrincipal().equals("anonymousUser"))) {

            boolean isAdmin = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .anyMatch("ROLE_ADMIN"::equals);

            boolean isExaminer = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .anyMatch("ROLE_EXAMINER"::equals);

            if (isAdmin) {
                // If ADMIN, redirect to admin dashboard
                return "redirect:/admin/dashboard";
            } else if (isExaminer) {
                // If EXAMINER, redirect to examiner dashboard
                return "redirect:/examiner/dashboard";
            } else {
                // If STUDENT, redirect to student dashboard
                return "redirect:/student/dashboard";
            }
        }

        return "index";
    }

    @GetMapping("/login")
    public String loginForm() {
        return "login";
    }

    @GetMapping("/access-denied")
    public String accessDenied() {
        return "access_denied";
    }

    @GetMapping("/register")
    public String showRegistrationForm(Model model) {
        model.addAttribute("user", new User());
        return "register";
    }

    @PostMapping("/register")
    public String registerStudent(
            @ModelAttribute("user") User user,
            BindingResult bindingResult,
            @RequestParam("profilePicFile") MultipartFile profilePicFile, // Gets the file
            Model model) {

        // Check if user (email) already exists
        if (userService.findByUsername(user.getUsername()) != null) {
            bindingResult.rejectValue("username", "error.user", "An account already exists with that email.");
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("user", user);
            return "register";
        }

        if (!profilePicFile.isEmpty()) {
            try {

                String filePath = fileStorageService.saveFile(profilePicFile);
                user.setProfilePicUrl(filePath); // Save the path to the user
            } catch (Exception e) {
                // Handle file save error
                model.addAttribute("user", user);
                model.addAttribute("fileError", "Could not save profile picture. Please try again.");
                return "register";
            }
        }

        userService.saveStudent(user);
        return "redirect:/register?success";
    }
}
/***
 * Subscribe Lazycoder -
 * https://www.youtube.com/c/LazyCoderOnline?sub_confirmation=1
 * whatsapp - https://wa.me/919572181024
 * email - wapka1503@gmail.com
 ***/