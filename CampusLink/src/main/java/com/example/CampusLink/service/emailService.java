package com.example.CampusLink.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class emailService {

    @Autowired
    private JavaMailSender mailSender;

    @Async("emailTaskExecutor")
    public void enviarCodigo(String para, String codigo) {

        SimpleMailMessage mensagem = new SimpleMailMessage();

        mensagem.setFrom("tccumcriqedmat@gmail.com");
        mensagem.setTo(para);
        mensagem.setSubject("Código de verificação - 2FA");
        mensagem.setText("Seu código de verificação é: " + codigo);

        mailSender.send(mensagem);

        System.out.println("Email enviado com sucesso!");
    }

    @Async("emailTaskExecutor")
    public void enviarEmailDados(String para, String Nome, String Sobrenome, String Email, String DT_Reg) {

        SimpleMailMessage mensagem = new SimpleMailMessage();

        mensagem.setFrom("tccumcriqedmat@gmail.com");
        mensagem.setTo(para);
        mensagem.setSubject("Dados pessoais - Exportação de dados");
        String corpoEmail =
                "Nome: " + Nome + "\n" +
                "Sobrenome: " + Sobrenome + "\n" +
                "Email: " + Email + "\n" +
                "Data de Registro: " + DT_Reg;

        mensagem.setText(corpoEmail);

        mailSender.send(mensagem);

        System.out.println("Email enviado com sucesso!");
    }

}
