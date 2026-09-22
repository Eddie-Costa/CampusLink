package com.example.CampusLink.service;

import com.example.CampusLink.dao.RevisaoConteudoDAO;
import org.springframework.stereotype.Service;

import java.sql.SQLException;

@Service
public class RevisaoConteudoService {

    private final RevisaoConteudoDAO revisaoConteudoDAO;

    public RevisaoConteudoService(RevisaoConteudoDAO revisaoConteudoDAO) {
        this.revisaoConteudoDAO = revisaoConteudoDAO;
    }

    public boolean liberarConteudo(Long idConteudo) {

        try {
            return revisaoConteudoDAO.liberarConteudo(idConteudo);
        } catch (SQLException e) {
            throw new RuntimeException("Não foi possível liberar o conteúdo.", e);
        }
    }

    public boolean reprovarConteudo(Long idConteudo, String comentarioAdmin) {

        if (comentarioAdmin == null || comentarioAdmin.isBlank()) {
            return false;
        }

        try {
            return revisaoConteudoDAO.reprovarConteudo(idConteudo, comentarioAdmin);
        } catch (SQLException e) {
            throw new RuntimeException("Não foi possível reprovar o conteúdo.", e);
        }
    }
}