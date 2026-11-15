package com.example.CampusLink.controller.Usuarios.Admin;

import com.example.CampusLink.dto.Admin.SessaoLogDTO;
import com.example.CampusLink.service.SessaoLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;

// as duas rotas usam a regra /admin/** do SecurityConfig e permitem somente consulta
@Controller
public class AdminSessaoLogsController {
    private static final Logger logger = LoggerFactory.getLogger(AdminSessaoLogsController.class);
    private final SessaoLogService sessoes;

    public AdminSessaoLogsController(SessaoLogService sessoes) {
        this.sessoes = sessoes;
    }

    @GetMapping("/admin/logs-sessoes")
    public String listar(Model model,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "") String busca,
            @RequestParam(required = false) Long usuarioId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicial,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFinal,
            @RequestParam(required = false) Integer duracaoMin,
            @RequestParam(required = false) Integer duracaoMax) {
        busca = busca.trim();
        model.addAttribute("busca", busca);
        model.addAttribute("usuarioId", usuarioId);
        model.addAttribute("dataInicial", dataInicial);
        model.addAttribute("dataFinal", dataFinal);
        model.addAttribute("duracaoMin", duracaoMin);
        model.addAttribute("duracaoMax", duracaoMax);
        model.addAttribute("sessoes", Page.empty());
        try {
            model.addAttribute("sessoes", sessoes.listar(pagina, busca, usuarioId, dataInicial, dataFinal, duracaoMin, duracaoMax));
        } catch (IllegalArgumentException e) {
            model.addAttribute("mensagemErro", e.getMessage());
        } catch (SQLException e) {
            logger.error("Não foi possível consultar o histórico de sessões.", e);
            model.addAttribute("mensagemErro", "Não foi possível carregar as sessões. Tente novamente mais tarde.");
        }
        return "Usuarios/Admin/logsSessoesAdmin";
    }

    @GetMapping("/admin/logs-sessoes/{id}")
    public String visualizar(@PathVariable UUID id, Model model) {
        try {
            SessaoLogDTO sessao = sessoes.buscar(id);
            if (sessao == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sessão encerrada não encontrada.");
            model.addAttribute("sessao", sessao);
            return "Usuarios/Admin/detalheSessaoLogAdmin";
        } catch (SQLException e) {
            logger.error("Não foi possível consultar os detalhes da sessão.", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Não foi possível carregar os logs.");
        }
    }
}
