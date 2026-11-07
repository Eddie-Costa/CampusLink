// paineis por perfil recebe os filtros e os pedidos de visualizacao dos dashboards
package com.example.CampusLink.controller.Geral;

import com.example.CampusLink.dto.Aluno.loginAlunoDTO;
import com.example.CampusLink.dto.Professor.loginProfessorDTO;
import com.example.CampusLink.dto.Admin.loginAdminDTO;
import com.example.CampusLink.dto.DashboardFiltro;
import com.example.CampusLink.repository.DashboardRepository;
import com.example.CampusLink.service.DashboardService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

// acesso aos dashboards esta classe confere identidade perfil e turma antes de carregar os indicadores
@Controller
public class DashboardController {
    private final DashboardRepository repository;
    private final DashboardService service;

    // preparar controle dos dashboards este construtor recebe as consultas e os resumos dos paineis
    public DashboardController(DashboardRepository repository, DashboardService service) {
        this.repository = repository;
        this.service = service;
    }

    // abrir meu dashboard este metodo direciona o usuario ao painel do seu perfil
    @GetMapping("/dashboard")
    public String meuDashboard(HttpSession session) {
        String tipo = tipoAutenticado(session);
        return tipo == null ? "redirect:/login" : "redirect:/dashboard/" + tipo;
    }

    // consultar dashboard do perfil este metodo valida o acesso e carrega os indicadores com os filtros
    @GetMapping("/dashboard/{tipo}")
    public String dashboard(@PathVariable String tipo, HttpSession session, Model model,
            @RequestParam(defaultValue = "todos") String periodo,
            @RequestParam(defaultValue = "0") long turma,
            @RequestParam(defaultValue = "todos") String prioridade,
            @RequestParam(defaultValue = "todos") String perfil) {
        String autenticado = tipoAutenticado(session);
        // proteger dashboard encaminha sessoes nao reconhecidas para o login
        if (autenticado == null) return "redirect:/login";
        // conferir perfil do dashboard recusa a pagina de um perfil diferente do autenticado
        if (!tipo.equals(autenticado)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);

        Long id = repository.buscarIdentidade(session.getAttribute("email2FA").toString().trim(), tipo);
        // conferir identidade do dashboard recusa o acesso quando o usuario nao foi identificado
        if (id == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN);

        DashboardFiltro filtro = new DashboardFiltro(periodo, "admin".equals(tipo) ? 0 : turma,
                "aluno".equals(tipo) ? prioridade : "todos", "admin".equals(tipo) ? perfil : "todos");
        var opcoes = "admin".equals(tipo) ? List.<java.util.Map<String,Object>>of()
                : repository.listarTurmas(repository.parametros(tipo, id, new DashboardFiltro("todos", 0, "todos", "todos")));
        // conferir turma do dashboard recusa filtros de turmas fora das opcoes permitidas
        if (filtro.getTurma() != 0 && opcoes.stream().noneMatch(t -> ((Number) t.get("id")).longValue() == filtro.getTurma())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        model.addAttribute("tipo", tipo);
        model.addAttribute("filtro", filtro);
        model.addAttribute("opcoesTurmas", opcoes);
        model.addAttribute("dados", switch (tipo) {
            case "aluno" -> service.aluno(id, filtro);
            case "professor" -> service.professor(id, filtro);
            default -> service.admin(id, filtro);
        });
        return "Dashboards/" + tipo;
    }

    // validar perfil do dashboard este metodo confere se o perfil e o email correspondem ao usuario conectado
    private String tipoAutenticado(HttpSession session) {
        Object tipo = session.getAttribute("tipoUsuario");
        Object email = session.getAttribute("email2FA");
        Object usuario = session.getAttribute("usuarioLogado");
        // validar sessao do dashboard rejeita perfil desconhecido ou email ausente
        if (!(tipo instanceof String perfil) || !List.of("aluno", "professor", "admin").contains(perfil)
                || !(email instanceof String emailSessao) || emailSessao.isBlank()) return null;
        String emailUsuario = null;
        // obter email do aluno confere o tipo dos dados antes de usar o email da sessao
        if ("aluno".equals(perfil) && usuario instanceof loginAlunoDTO aluno) emailUsuario = aluno.getEmail();
        // obter email do professor confere o tipo dos dados antes de usar o email da sessao
        if ("professor".equals(perfil) && usuario instanceof loginProfessorDTO professor) emailUsuario = professor.getEmail();
        // obter email do administrador confere o tipo dos dados antes de usar o email da sessao
        if ("admin".equals(perfil) && usuario instanceof loginAdminDTO admin) emailUsuario = admin.getEmail();
        return emailUsuario != null && emailUsuario.trim().equalsIgnoreCase(emailSessao.trim()) ? perfil : null;
    }
}
