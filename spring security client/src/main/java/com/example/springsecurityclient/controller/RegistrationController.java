package com.example.springsecurityclient.controller;

import com.example.springsecurityclient.entity.User;
import com.example.springsecurityclient.entity.VerificationToken;
import com.example.springsecurityclient.event.RegistrationCompleteEvent;
import com.example.springsecurityclient.model.PasswordModel;
import com.example.springsecurityclient.model.UserModel;
import com.example.springsecurityclient.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@RestController
public class RegistrationController {
    @Autowired
    private UserService userService;

    @Autowired
    private ApplicationEventPublisher publisher;

    @PostMapping("/register")
    public String registerUser(@RequestBody UserModel userModel, final HttpServletRequest request){
        User user = userService.registerUser(userModel);
        publisher.publishEvent(
                new RegistrationCompleteEvent(user,applicationUrl(request))
        );
        return "Success";
    }

    @GetMapping("/verifyRegistration")
    public String verifyRegistration(@RequestParam("token")String token){
        String result=userService.validateVerificationToken(token);
        if(result.equalsIgnoreCase("valid")){
            return "User Verifies Successfully";
        }
        return "Bad User";
    }

    @GetMapping("/hello")
    public String hello(){
        return "Hello World";
    }


    @GetMapping("/resendVerifyToken")
    public String resendVerificationToken(@RequestParam("token")String oldToken,
                                          HttpServletRequest request){
        VerificationToken verificationToken=
                userService.generateNewVerificationToken(oldToken);
        User user=verificationToken.getUser();
        resendVerificationTokenEmail(user,applicationUrl(request),verificationToken);
        return "Verification Link Sent";
    }

    private void resendVerificationTokenEmail(User user, String applicationUrl,VerificationToken token) {
        String url = applicationUrl
                +"/verifyRegistration?token="+token;
        //send verification email
        log.info("Click the link to verify your account: {}",url);
    }

    @PostMapping("/resetPassword")
    public String resetPassword(@RequestBody PasswordModel passwordModel,HttpServletRequest request){
        User user=userService.findUserByEmail(passwordModel.getEmail());
        String url="";
        if(user!=null){
            String token= UUID.randomUUID().toString();
            userService.createPasswordResetTokenForUser(user,token);
            url = passwordResetTokenMail(user,applicationUrl(request),token);
        }
        return url;
    }

    @PostMapping("/savePassword")
    public String savePassword(@RequestParam("token")String token,
                               @RequestBody PasswordModel passwordModel){
        String result=userService.validatePasswordResetToken(token);
        if(!result.equalsIgnoreCase("valid")){
            return "Invalid Token";
        }
        Optional<User> user = userService.getUserByPasswordResetToken(token);
        if(user.isPresent()){
            userService.changePassword(user.get(),passwordModel.getNewPassword());
            return "Password Reset Successfully";
        }else {
            return "Invalid Token";
        }
    }

    @PostMapping("/changePassword")
    private String changePassword(@RequestBody PasswordModel passwordModel){
        User user = userService.findUserByEmail(passwordModel.getEmail());
        if(!userService.checkIfValidOldPassword(user,passwordModel.getOldPassword())){
            return "Invalid Old Password";
        }
        userService.changePassword(user,passwordModel.getNewPassword());
        return "Password Changed Successfully";
    }

    private String passwordResetTokenMail(User user, String applicationUrl, String token) {

        String url = applicationUrl
                +"/savePassword?token="+token;
        //send verification email
        log.info("Click the link to reset your password : {}",url);
        return url;
    }

    private String applicationUrl(HttpServletRequest request) {
        return "http://"+
                request.getServerName()+
                ":"+
                request.getServerPort()+
                request.getContextPath();
    }
}
