package com.example.CampusLink.controller.Geral;

import com.example.CampusLink.controller.Usuarios.Admin.AdminChamadosController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import java.util.Map;

@ControllerAdvice(assignableTypes = {ChamadoController.class, AdminChamadosController.class})
public class ChamadoErroHandler {

    private static final Logger logger = LoggerFactory.getLogger(ChamadoErroHandler.class);

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public String erroBanco(Exception erro) {
        logger.error("Erro ao acessar os chamados", erro);
        return "Geral/erroChamados";
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    @ResponseBody
    public ResponseEntity<Map<String, String>> erroMensagem(RuntimeException erro) {
        HttpStatus status = erro instanceof IllegalStateException ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(Map.of("mensagem", erro.getMessage()));
    }
}
