// turmas administrativas disponibiliza cadastro edicao e gerenciamento de participantes

package com.example.CampusLink.controller.Usuarios.Admin;

import org.slf4j.helpers.MessageFormatter;
import java.io.StringWriter;
import java.io.PrintWriter;
import com.example.CampusLink.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import com.example.CampusLink.repository.AdminTurmaRepository;
import com.example.CampusLink.dto.Admin.cadastrarTurmaAdminDTO;
import com.example.CampusLink.dto.Admin.editarTurmaAdminDTO;
import com.example.CampusLink.dto.Admin.loginAdminDTO;
import com.example.CampusLink.dto.Admin.turmaAdminDTO;
import com.example.CampusLink.dto.Admin.usuarioAdminDTO;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

// controle administrativo de turmas esta classe confere o acesso e encaminha alteracoes de vinculos
@Controller
public class AdminTurmasController {

    @Autowired
    private UsuarioRepository UsuarioRepository;

    private static final Logger logger = LoggerFactory.getLogger(AdminTurmasController.class);

    private static final String URL_LOGIN_ADMIN = "/admin/login";

    private final AdminTurmaRepository adminTurmaRepository;

    // preparar controle administrativo de turmas este construtor recebe as consultas e alteracoes de turmas
    public AdminTurmasController(AdminTurmaRepository adminTurmaRepository) {
        this.adminTurmaRepository = adminTurmaRepository;
    }

    // listar turmas administrativas este metodo carrega as turmas e informa falhas de consulta
    @GetMapping("/admin/turmas")
    public String listarTurmas(HttpSession session, Model model) {

        // exigir login administrativo encaminha sessoes sem usuario para o acesso do administrador
        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:" + URL_LOGIN_ADMIN;
        }

        // exigir administrador retorna ao inicio quando a sessao nao comprova o acesso administrativo
        if (!ehAdministrador(session)) {
            return "redirect:/home";
        }

        // carregar turmas administrativas consulta os registros e informa falha com uma lista vazia
        try {
            List<turmaAdminDTO> turmas = adminTurmaRepository.listarTurmas();
            model.addAttribute("turmas", turmas);

        } catch (SQLException e) {
            logger.error("erro ao carregar turmas para o administrador", e);
            // registro de erros confere se esta habilitado ao listar turmas administrativas
            if (logger.isErrorEnabled()) {
                // gravar historico ao listar turmas administrativas tenta salvar o registro no banco e informa falhas
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", AdminTurmasController.class.getName(), "listarTurmas", null,
                            "erro ao carregar turmas para o administrador", null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            model.addAttribute("mensagemErro", "Não foi possível carregar as turmas.");
            model.addAttribute("turmas", new ArrayList<turmaAdminDTO>());
        }

        return "Usuarios/Admin/turmasAdmin";
    }

    // abrir cadastro administrativo de turma este metodo prepara o formulario e os professores ativos
    @GetMapping("/admin/turmas/cadastrar")
    public String exibirCadastroTurma(HttpSession session, Model model) {

        // exigir login administrativo encaminha sessoes sem usuario para o acesso do administrador
        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:" + URL_LOGIN_ADMIN;
        }

        // exigir administrador retorna ao inicio quando a sessao nao comprova o acesso administrativo
        if (!ehAdministrador(session)) {
            return "redirect:/home";
        }

        // preparar cadastro administrativo de turma carrega professores ativos e trata falhas da consulta
        try {
            List<usuarioAdminDTO> professores = adminTurmaRepository.listarProfessoresAtivos();

            model.addAttribute("turma", new cadastrarTurmaAdminDTO());
            model.addAttribute("professores", professores);

        } catch (SQLException e) {
            logger.error("erro ao carregar professores para cadastro de turma", e);
            // registro de erros confere se esta habilitado ao abrir cadastro administrativo de turma
            if (logger.isErrorEnabled()) {
                // gravar historico ao abrir cadastro administrativo de turma tenta salvar o registro no banco e informa falhas
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", AdminTurmasController.class.getName(), "exibirCadastroTurma", null,
                            "erro ao carregar professores para cadastro de turma", null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            model.addAttribute("turma", new cadastrarTurmaAdminDTO());
            model.addAttribute("professores", new ArrayList<usuarioAdminDTO>());
            model.addAttribute("mensagemErro", "Não foi possível carregar os professores.");
        }

        return "Usuarios/Admin/cadastrarTurmaAdmin";
    }

    // cadastrar turma pela administracao este metodo valida o formulario e grava a nova turma
    @PostMapping("/admin/turmas/cadastrar")
    public String cadastrarTurma(
            @Valid @ModelAttribute("turma") cadastrarTurmaAdminDTO turma,
            BindingResult bindingResult,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes) {

        // exigir login administrativo encaminha sessoes sem usuario para o acesso do administrador
        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:" + URL_LOGIN_ADMIN;
        }

        // exigir administrador retorna ao inicio quando a sessao nao comprova o acesso administrativo
        if (!ehAdministrador(session)) {
            return "redirect:/home";
        }

        // corrigir nova turma administrativa reapresenta o formulario e seus professores quando existem erros
        if (bindingResult.hasErrors()) {
            carregarProfessores(model);
            return "Usuarios/Admin/cadastrarTurmaAdmin";
        }

        // salvar turma administrativa tenta cadastrar a turma e recupera as opcoes se o banco falhar
        try {
            adminTurmaRepository.cadastrarTurma(turma);
            logger.info("Turma cadastrada pelo administrador. nome={} professorId={}", turma.getNomeTurma(), turma.getIdProprietario());
            // registro de informacoes confere se esta habilitado ao cadastrar turma pela administracao
            if (logger.isInfoEnabled()) {
                // gravar historico ao cadastrar turma pela administracao tenta salvar o registro no banco e informa falhas
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "INFO", AdminTurmasController.class.getName(), "cadastrarTurma", null,
                            MessageFormatter.arrayFormat("Turma cadastrada pelo administrador. nome={} professorId={}", new Object[]{turma.getNomeTurma(), turma.getIdProprietario()}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            redirectAttributes.addFlashAttribute("mensagemSucesso", "Turma cadastrada com sucesso!");

            return "redirect:/admin/turmas";

        } catch (SQLException e) {
            logger.error("erro ao cadastrar turma", e);
            // registro de erros confere se esta habilitado ao cadastrar turma pela administracao
            if (logger.isErrorEnabled()) {
                // gravar historico ao cadastrar turma pela administracao tenta salvar o registro no banco e informa falhas
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", AdminTurmasController.class.getName(), "cadastrarTurma", null,
                            "erro ao cadastrar turma", null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            carregarProfessores(model);
            model.addAttribute("mensagemErro", "Não foi possível cadastrar a turma.");

            return "Usuarios/Admin/cadastrarTurmaAdmin";
        }
    }

    // abrir edicao administrativa de turma este metodo carrega a turma e as opcoes de responsavel
    @GetMapping("/admin/turmas/{idTurma}/editar")
    public String exibirEdicaoTurma(
            @PathVariable Long idTurma,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes) {

        // exigir login administrativo encaminha sessoes sem usuario para o acesso do administrador
        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:" + URL_LOGIN_ADMIN;
        }

        // exigir administrador retorna ao inicio quando a sessao nao comprova o acesso administrativo
        if (!ehAdministrador(session)) {
            return "redirect:/home";
        }

        // preparar edicao administrativa de turma busca a turma e os professores e informa falhas
        try {
            editarTurmaAdminDTO turma = adminTurmaRepository.buscarTurmaPorId(idTurma);

            // buscar turma administrativa avisa quando a turma nao existe e retorna a listagem
            if (turma == null) {
                redirectAttributes.addFlashAttribute("mensagemErro", "Turma não encontrada.");
                return "redirect:/admin/turmas";
            }

            model.addAttribute("turma", turma);
            model.addAttribute("professores", adminTurmaRepository.listarProfessoresAtivos());

            return "Usuarios/Admin/editarTurmaAdmin";

        } catch (SQLException e) {
            logger.error("erro ao carregar turma para edição", e);
            // registro de erros confere se esta habilitado ao abrir edicao administrativa de turma
            if (logger.isErrorEnabled()) {
                // gravar historico ao abrir edicao administrativa de turma tenta salvar o registro no banco e informa falhas
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", AdminTurmasController.class.getName(), "exibirEdicaoTurma", null,
                            "erro ao carregar turma para edição", null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            redirectAttributes.addFlashAttribute("mensagemErro", "Não foi possível carregar a turma.");

            return "redirect:/admin/turmas";
        }
    }

    // editar turma pela administracao este metodo valida o formulario e atualiza a turma existente
    @PostMapping("/admin/turmas/{idTurma}/editar")
    public String editarTurma(
            @PathVariable Long idTurma,
            @Valid @ModelAttribute("turma") editarTurmaAdminDTO turma,
            BindingResult bindingResult,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes) {

        // exigir login administrativo encaminha sessoes sem usuario para o acesso do administrador
        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:" + URL_LOGIN_ADMIN;
        }

        // exigir administrador retorna ao inicio quando a sessao nao comprova o acesso administrativo
        if (!ehAdministrador(session)) {
            return "redirect:/home";
        }

        turma.setId(idTurma);

        // corrigir turma administrativa reapresenta a edicao com os professores quando existem erros
        if (bindingResult.hasErrors()) {
            carregarProfessores(model);
            return "Usuarios/Admin/editarTurmaAdmin";
        }

        // atualizar turma administrativa confere existencia salva os dados e trata falhas do banco
        try {
            editarTurmaAdminDTO turmaExistente = adminTurmaRepository.buscarTurmaPorId(idTurma);

            // localizar turma antes de salvar interrompe a edicao quando a turma nao foi encontrada
            if (turmaExistente == null) {
                redirectAttributes.addFlashAttribute("mensagemErro", "Turma não encontrada.");
                return "redirect:/admin/turmas";
            }

            boolean atualizado = adminTurmaRepository.atualizarTurma(turma);

            // informar turma nao atualizada reapresenta os dados quando a gravacao nao alterou a turma
            if (!atualizado) {
                logger.warn("Atualização de turma não realizada. turmaId={}", idTurma);
                // registro de avisos confere se esta habilitado ao editar turma pela administracao
                if (logger.isWarnEnabled()) {
                    // gravar historico ao editar turma pela administracao tenta salvar o registro no banco e informa falhas
                    try {
                        UsuarioRepository.InserirLogsNoBD(null, "WARN", AdminTurmasController.class.getName(), "editarTurma", null,
                                MessageFormatter.arrayFormat("Atualização de turma não realizada. turmaId={}", new Object[]{idTurma}).getMessage(), null, null);
                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }
                carregarProfessores(model);
                model.addAttribute("mensagemErro", "Não foi possível atualizar a turma.");
                return "Usuarios/Admin/editarTurmaAdmin";
            }

            logger.info("Turma atualizada pelo administrador. turmaId={} professorId={}", idTurma, turma.getIdProprietario());
            // registro de informacoes confere se esta habilitado ao editar turma pela administracao
            if (logger.isInfoEnabled()) {
                // gravar historico ao editar turma pela administracao tenta salvar o registro no banco e informa falhas
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "INFO", AdminTurmasController.class.getName(), "editarTurma", null,
                            MessageFormatter.arrayFormat("Turma atualizada pelo administrador. turmaId={} professorId={}", new Object[]{idTurma, turma.getIdProprietario()}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            redirectAttributes.addFlashAttribute("mensagemSucesso", "Turma atualizada com sucesso!");

            return "redirect:/admin/turmas";

        } catch (SQLException e) {
            logger.error("erro ao atualizar turma", e);
            // registro de erros confere se esta habilitado ao editar turma pela administracao
            if (logger.isErrorEnabled()) {
                // gravar historico ao editar turma pela administracao tenta salvar o registro no banco e informa falhas
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", AdminTurmasController.class.getName(), "editarTurma", null,
                            "erro ao atualizar turma", null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            carregarProfessores(model);
            model.addAttribute("mensagemErro", "Não foi possível atualizar a turma.");

            return "Usuarios/Admin/editarTurmaAdmin";
        }
    }

    // listar alunos da turma na administracao este metodo carrega participantes e alunos disponiveis para inclusao
    @GetMapping("/admin/turmas/{idTurma}/alunos")
    public String listarAlunosDaTurma(
            @PathVariable Long idTurma,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes) {

        // exigir login administrativo encaminha sessoes sem usuario para o acesso do administrador
        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:" + URL_LOGIN_ADMIN;
        }

        // exigir administrador retorna ao inicio quando a sessao nao comprova o acesso administrativo
        if (!ehAdministrador(session)) {
            return "redirect:/home";
        }

        // carregar alunos da turma consulta vinculados e disponiveis e retorna a lista de turmas se houver falha
        try {
            editarTurmaAdminDTO turma = adminTurmaRepository.buscarTurmaPorId(idTurma);

            // buscar turma administrativa avisa quando a turma nao existe e retorna a listagem
            if (turma == null) {
                redirectAttributes.addFlashAttribute("mensagemErro", "Turma não encontrada.");
                return "redirect:/admin/turmas";
            }

            List<usuarioAdminDTO> alunos = adminTurmaRepository.listarAlunosDaTurma(idTurma);
            List<usuarioAdminDTO> alunosDisponiveis = adminTurmaRepository.listarAlunosDisponiveis(idTurma);

            model.addAttribute("turma", turma);
            model.addAttribute("alunos", alunos);
            model.addAttribute("alunosDisponiveis", alunosDisponiveis);

            return "Usuarios/Admin/alunosTurmaAdmin";

        } catch (SQLException e) {
            logger.error("erro ao carregar alunos da turma", e);
            // registro de erros confere se esta habilitado ao listar alunos da turma na administracao
            if (logger.isErrorEnabled()) {
                // gravar historico ao listar alunos da turma na administracao tenta salvar o registro no banco e informa falhas
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", AdminTurmasController.class.getName(), "listarAlunosDaTurma", null,
                            "erro ao carregar alunos da turma", null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            redirectAttributes.addFlashAttribute("mensagemErro", "Não foi possível carregar os alunos da turma.");

            return "redirect:/admin/turmas";
        }
    }

    // adicionar aluno pela administracao este metodo confere a turma e solicita o novo vinculo
    @PostMapping("/admin/turmas/{idTurma}/alunos/adicionar")
    public String adicionarAlunoNaTurma(
            @PathVariable Long idTurma,
            @RequestParam("idAluno") Long idAluno,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        // exigir login administrativo encaminha sessoes sem usuario para o acesso do administrador
        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:" + URL_LOGIN_ADMIN;
        }

        // exigir administrador retorna ao inicio quando a sessao nao comprova o acesso administrativo
        if (!ehAdministrador(session)) {
            return "redirect:/home";
        }

        // adicionar aluno administrativamente confere a turma cria o vinculo e informa falhas de gravacao
        try {
            editarTurmaAdminDTO turma = adminTurmaRepository.buscarTurmaPorId(idTurma);

            // buscar turma administrativa avisa quando a turma nao existe e retorna a listagem
            if (turma == null) {
                redirectAttributes.addFlashAttribute("mensagemErro", "Turma não encontrada.");
                return "redirect:/admin/turmas";
            }

            boolean adicionado = adminTurmaRepository.adicionarAlunoNaTurma(idTurma, idAluno);

            // informar aluno nao vinculado interrompe a confirmacao quando a inclusao nao aconteceu
            if (!adicionado) {
                logger.warn("Inclusão de aluno não realizada. turmaId={} alunoId={}", idTurma, idAluno);
                // registro de avisos confere se esta habilitado ao adicionar aluno pela administracao
                if (logger.isWarnEnabled()) {
                    // gravar historico ao adicionar aluno pela administracao tenta salvar o registro no banco e informa falhas
                    try {
                        UsuarioRepository.InserirLogsNoBD(null, "WARN", AdminTurmasController.class.getName(), "adicionarAlunoNaTurma", null,
                                MessageFormatter.arrayFormat("Inclusão de aluno não realizada. turmaId={} alunoId={}", new Object[]{idTurma, idAluno}).getMessage(), null, null);
                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }
                redirectAttributes.addFlashAttribute("mensagemErro", "Não foi possível adicionar o aluno à turma.");
                return "redirect:/admin/turmas/" + idTurma + "/alunos";
            }

            logger.info("Aluno adicionado à turma pelo administrador. turmaId={} alunoId={}", idTurma, idAluno);
            // registro de informacoes confere se esta habilitado ao adicionar aluno pela administracao
            if (logger.isInfoEnabled()) {
                // gravar historico ao adicionar aluno pela administracao tenta salvar o registro no banco e informa falhas
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "INFO", AdminTurmasController.class.getName(), "adicionarAlunoNaTurma", null,
                            MessageFormatter.arrayFormat("Aluno adicionado à turma pelo administrador. turmaId={} alunoId={}", new Object[]{idTurma, idAluno}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            redirectAttributes.addFlashAttribute("mensagemSucesso", "Aluno adicionado à turma com sucesso!");

            return "redirect:/admin/turmas/" + idTurma + "/alunos";

        } catch (SQLException e) {
            logger.error("erro ao adicionar aluno na turma", e);
            // registro de erros confere se esta habilitado ao adicionar aluno pela administracao
            if (logger.isErrorEnabled()) {
                // gravar historico ao adicionar aluno pela administracao tenta salvar o registro no banco e informa falhas
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", AdminTurmasController.class.getName(), "adicionarAlunoNaTurma", null,
                            "erro ao adicionar aluno na turma", null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            redirectAttributes.addFlashAttribute("mensagemErro", "Não foi possível adicionar o aluno à turma.");

            return "redirect:/admin/turmas/" + idTurma + "/alunos";
        }
    }

    // delete aluno da turma pela administracao este metodo remove a participacao e informa o resultado
    @PostMapping("/admin/turmas/{idTurma}/alunos/{idAluno}/remover")
    public String removerAlunoDaTurma(
            @PathVariable Long idTurma,
            @PathVariable Long idAluno,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        // exigir login administrativo encaminha sessoes sem usuario para o acesso do administrador
        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:" + URL_LOGIN_ADMIN;
        }

        // exigir administrador retorna ao inicio quando a sessao nao comprova o acesso administrativo
        if (!ehAdministrador(session)) {
            return "redirect:/home";
        }

        // delete vinculo do aluno confere a turma remove o participante e informa falhas do banco
        try {
            editarTurmaAdminDTO turma = adminTurmaRepository.buscarTurmaPorId(idTurma);

            // buscar turma administrativa avisa quando a turma nao existe e retorna a listagem
            if (turma == null) {
                redirectAttributes.addFlashAttribute("mensagemErro", "Turma não encontrada.");
                return "redirect:/admin/turmas";
            }

            boolean removido = adminTurmaRepository.removerAlunoDaTurma(idTurma, idAluno);

            // informar aluno nao removido avisa quando o vinculo nao foi excluido
            if (!removido) {
                logger.warn("Remoção de aluno não realizada. turmaId={} alunoId={}", idTurma, idAluno);
                // registro de avisos confere se esta habilitado ao delete aluno da turma pela administracao
                if (logger.isWarnEnabled()) {
                    // gravar historico ao delete aluno da turma pela administracao tenta salvar o registro no banco e informa falhas
                    try {
                        UsuarioRepository.InserirLogsNoBD(null, "WARN", AdminTurmasController.class.getName(), "removerAlunoDaTurma", null,
                                MessageFormatter.arrayFormat("Remoção de aluno não realizada. turmaId={} alunoId={}", new Object[]{idTurma, idAluno}).getMessage(), null, null);
                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }
                redirectAttributes.addFlashAttribute("mensagemErro", "Não foi possível remover o aluno da turma.");
                return "redirect:/admin/turmas/" + idTurma + "/alunos";
            }

            logger.info("Aluno removido da turma pelo administrador. turmaId={} alunoId={}", idTurma, idAluno);
            // registro de informacoes confere se esta habilitado ao delete aluno da turma pela administracao
            if (logger.isInfoEnabled()) {
                // gravar historico ao delete aluno da turma pela administracao tenta salvar o registro no banco e informa falhas
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "INFO", AdminTurmasController.class.getName(), "removerAlunoDaTurma", null,
                            MessageFormatter.arrayFormat("Aluno removido da turma pelo administrador. turmaId={} alunoId={}", new Object[]{idTurma, idAluno}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            redirectAttributes.addFlashAttribute("mensagemSucesso", "Aluno removido da turma com sucesso!");

            return "redirect:/admin/turmas/" + idTurma + "/alunos";

        } catch (SQLException e) {
            logger.error("erro ao remover aluno da turma", e);
            // registro de erros confere se esta habilitado ao delete aluno da turma pela administracao
            if (logger.isErrorEnabled()) {
                // gravar historico ao delete aluno da turma pela administracao tenta salvar o registro no banco e informa falhas
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", AdminTurmasController.class.getName(), "removerAlunoDaTurma", null,
                            "erro ao remover aluno da turma", null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            redirectAttributes.addFlashAttribute("mensagemErro", "Não foi possível remover o aluno da turma.");

            return "redirect:/admin/turmas/" + idTurma + "/alunos";
        }
    }

    // listar professores da turma na administracao este metodo carrega participantes e professores disponiveis
    @GetMapping("/admin/turmas/{idTurma}/professores")
    public String listarProfessoresDaTurma(
            @PathVariable Long idTurma,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes) {

        // exigir login administrativo encaminha sessoes sem usuario para o acesso do administrador
        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:" + URL_LOGIN_ADMIN;
        }

        // exigir administrador retorna ao inicio quando a sessao nao comprova o acesso administrativo
        if (!ehAdministrador(session)) {
            return "redirect:/home";
        }

        // carregar professores da turma consulta vinculados e disponiveis e informa falhas do banco
        try {
            editarTurmaAdminDTO turma = adminTurmaRepository.buscarTurmaPorId(idTurma);

            // buscar turma administrativa avisa quando a turma nao existe e retorna a listagem
            if (turma == null) {
                redirectAttributes.addFlashAttribute("mensagemErro", "Turma não encontrada.");
                return "redirect:/admin/turmas";
            }

            List<usuarioAdminDTO> professores = adminTurmaRepository.listarProfessoresDaTurma(idTurma);
            List<usuarioAdminDTO> professoresDisponiveis = adminTurmaRepository.listarProfessoresDisponiveis(idTurma);

            model.addAttribute("turma", turma);
            model.addAttribute("professores", professores);
            model.addAttribute("professoresDisponiveis", professoresDisponiveis);

            return "Usuarios/Admin/professoresTurmaAdmin";

        } catch (SQLException e) {
            logger.error("erro ao carregar professores da turma", e);
            // registro de erros confere se esta habilitado ao listar professores da turma na administracao
            if (logger.isErrorEnabled()) {
                // gravar historico ao listar professores da turma na administracao tenta salvar o registro no banco e informa falhas
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", AdminTurmasController.class.getName(), "listarProfessoresDaTurma", null,
                            "erro ao carregar professores da turma", null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            redirectAttributes.addFlashAttribute("mensagemErro", "Não foi possível carregar os professores da turma.");

            return "redirect:/admin/turmas";
        }
    }

    // adicionar professor pela administracao este metodo confere a turma e solicita o novo vinculo
    @PostMapping("/admin/turmas/{idTurma}/professores/adicionar")
    public String adicionarProfessorNaTurma(
            @PathVariable Long idTurma,
            @RequestParam("idProfessor") Long idProfessor,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        // exigir login administrativo encaminha sessoes sem usuario para o acesso do administrador
        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:" + URL_LOGIN_ADMIN;
        }

        // exigir administrador retorna ao inicio quando a sessao nao comprova o acesso administrativo
        if (!ehAdministrador(session)) {
            return "redirect:/home";
        }

        // adicionar professor administrativamente cria o vinculo com a turma e trata falhas de gravacao
        try {
            editarTurmaAdminDTO turma = adminTurmaRepository.buscarTurmaPorId(idTurma);

            // buscar turma administrativa avisa quando a turma nao existe e retorna a listagem
            if (turma == null) {
                redirectAttributes.addFlashAttribute("mensagemErro", "Turma não encontrada.");
                return "redirect:/admin/turmas";
            }

            boolean adicionado = adminTurmaRepository.adicionarProfessorNaTurma(idTurma, idProfessor);

            // informar professor nao vinculado avisa quando a inclusao nao aconteceu
            if (!adicionado) {
                logger.warn("Inclusão de professor não realizada. turmaId={} professorId={}", idTurma, idProfessor);
                // registro de avisos confere se esta habilitado ao adicionar professor pela administracao
                if (logger.isWarnEnabled()) {
                    // gravar historico ao adicionar professor pela administracao tenta salvar o registro no banco e informa falhas
                    try {
                        UsuarioRepository.InserirLogsNoBD(null, "WARN", AdminTurmasController.class.getName(), "adicionarProfessorNaTurma", null,
                                MessageFormatter.arrayFormat("Inclusão de professor não realizada. turmaId={} professorId={}", new Object[]{idTurma, idProfessor}).getMessage(), null, null);
                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }
                redirectAttributes.addFlashAttribute("mensagemErro", "Não foi possível adicionar o professor à turma.");
                return "redirect:/admin/turmas/" + idTurma + "/professores";
            }

            logger.info("Professor adicionado à turma pelo administrador. turmaId={} professorId={}", idTurma, idProfessor);
            // registro de informacoes confere se esta habilitado ao adicionar professor pela administracao
            if (logger.isInfoEnabled()) {
                // gravar historico ao adicionar professor pela administracao tenta salvar o registro no banco e informa falhas
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "INFO", AdminTurmasController.class.getName(), "adicionarProfessorNaTurma", null,
                            MessageFormatter.arrayFormat("Professor adicionado à turma pelo administrador. turmaId={} professorId={}", new Object[]{idTurma, idProfessor}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            redirectAttributes.addFlashAttribute("mensagemSucesso", "Professor adicionado à turma com sucesso!");

            return "redirect:/admin/turmas/" + idTurma + "/professores";

        } catch (SQLException e) {
            logger.error("erro ao adicionar professor na turma", e);
            // registro de erros confere se esta habilitado ao adicionar professor pela administracao
            if (logger.isErrorEnabled()) {
                // gravar historico ao adicionar professor pela administracao tenta salvar o registro no banco e informa falhas
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", AdminTurmasController.class.getName(), "adicionarProfessorNaTurma", null,
                            "erro ao adicionar professor na turma", null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            redirectAttributes.addFlashAttribute("mensagemErro", "Não foi possível adicionar o professor à turma.");

            return "redirect:/admin/turmas/" + idTurma + "/professores";
        }
    }

    // delete professor da turma pela administracao este metodo impede retirar o responsavel e remove o participante
    @PostMapping("/admin/turmas/{idTurma}/professores/{idProfessor}/remover")
    public String removerProfessorDaTurma(
            @PathVariable Long idTurma,
            @PathVariable Long idProfessor,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        // exigir login administrativo encaminha sessoes sem usuario para o acesso do administrador
        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:" + URL_LOGIN_ADMIN;
        }

        // exigir administrador retorna ao inicio quando a sessao nao comprova o acesso administrativo
        if (!ehAdministrador(session)) {
            return "redirect:/home";
        }

        // delete vinculo do professor confere turma e responsavel antes de remover e trata falhas do banco
        try {
            editarTurmaAdminDTO turma = adminTurmaRepository.buscarTurmaPorId(idTurma);

            // buscar turma administrativa avisa quando a turma nao existe e retorna a listagem
            if (turma == null) {
                redirectAttributes.addFlashAttribute("mensagemErro", "Turma não encontrada.");
                return "redirect:/admin/turmas";
            }


            // preservar proprietario da turma exige trocar o responsavel antes de remover seu vinculo
            if (turma.getIdProprietario() != null && turma.getIdProprietario().equals(idProfessor)) {
                logger.warn("Remoção recusada: professor responsável pela turma. turmaId={} professorId={}", idTurma, idProfessor);
                // registro de avisos confere se esta habilitado ao delete professor da turma pela administracao
                if (logger.isWarnEnabled()) {
                    // gravar historico ao delete professor da turma pela administracao tenta salvar o registro no banco e informa falhas
                    try {
                        UsuarioRepository.InserirLogsNoBD(null, "WARN", AdminTurmasController.class.getName(), "removerProfessorDaTurma", null,
                                MessageFormatter.arrayFormat("Remoção recusada: professor responsável pela turma. turmaId={} professorId={}", new Object[]{idTurma, idProfessor}).getMessage(), null, null);
                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }
                redirectAttributes.addFlashAttribute(
                        "mensagemErro",
                        "O professor responsável pela turma não pode ser removido. Altere o responsável primeiro."
                );

                return "redirect:/admin/turmas/" + idTurma + "/professores";
            }

            boolean removido = adminTurmaRepository.removerProfessorDaTurma(idTurma, idProfessor);

            // informar professor nao removido avisa quando o vinculo nao foi excluido
            if (!removido) {
                logger.warn("Remoção de professor não realizada. turmaId={} professorId={}", idTurma, idProfessor);
                // registro de avisos confere se esta habilitado ao delete professor da turma pela administracao
                if (logger.isWarnEnabled()) {
                    // gravar historico ao delete professor da turma pela administracao tenta salvar o registro no banco e informa falhas
                    try {
                        UsuarioRepository.InserirLogsNoBD(null, "WARN", AdminTurmasController.class.getName(), "removerProfessorDaTurma", null,
                                MessageFormatter.arrayFormat("Remoção de professor não realizada. turmaId={} professorId={}", new Object[]{idTurma, idProfessor}).getMessage(), null, null);
                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }
                redirectAttributes.addFlashAttribute("mensagemErro", "Não foi possível remover o professor da turma.");
                return "redirect:/admin/turmas/" + idTurma + "/professores";
            }

            logger.info("Professor removido da turma pelo administrador. turmaId={} professorId={}", idTurma, idProfessor);
            // registro de informacoes confere se esta habilitado ao delete professor da turma pela administracao
            if (logger.isInfoEnabled()) {
                // gravar historico ao delete professor da turma pela administracao tenta salvar o registro no banco e informa falhas
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "INFO", AdminTurmasController.class.getName(), "removerProfessorDaTurma", null,
                            MessageFormatter.arrayFormat("Professor removido da turma pelo administrador. turmaId={} professorId={}", new Object[]{idTurma, idProfessor}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            redirectAttributes.addFlashAttribute("mensagemSucesso", "Professor removido da turma com sucesso!");

            return "redirect:/admin/turmas/" + idTurma + "/professores";

        } catch (SQLException e) {
            logger.error("erro ao remover professor da turma", e);
            // registro de erros confere se esta habilitado ao delete professor da turma pela administracao
            if (logger.isErrorEnabled()) {
                // gravar historico ao delete professor da turma pela administracao tenta salvar o registro no banco e informa falhas
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", AdminTurmasController.class.getName(), "removerProfessorDaTurma", null,
                            "erro ao remover professor da turma", null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            redirectAttributes.addFlashAttribute("mensagemErro", "Não foi possível remover o professor da turma.");

            return "redirect:/admin/turmas/" + idTurma + "/professores";
        }
    }


    // carregar opcoes de professores este metodo prepara a lista de professores ativos dos formularios
    private void carregarProfessores(Model model) {

        // carregar opcoes de professor busca contas ativas e usa lista vazia se a consulta falhar
        try {
            model.addAttribute("professores", adminTurmaRepository.listarProfessoresAtivos());

        } catch (SQLException e) {
            logger.error("erro ao carregar professores", e);
            // registro de erros confere se esta habilitado ao carregar opcoes de professores
            if (logger.isErrorEnabled()) {
                // gravar historico ao carregar opcoes de professores tenta salvar o registro no banco e informa falhas
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", AdminTurmasController.class.getName(), "carregarProfessores", null,
                            "erro ao carregar professores", null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            model.addAttribute("professores", new ArrayList<usuarioAdminDTO>());
        }
    }

    // validar administrador das turmas este metodo confere o perfil e o email da conta conectada
    private boolean ehAdministrador(HttpSession session) {

        Object tipoUsuario = session.getAttribute("tipoUsuario");
        Object usuarioLogado = session.getAttribute("usuarioLogado");
        Object emailSessao = session.getAttribute("email2FA");

        // conferir perfil administrativo rejeita sessoes com outro tipo de usuario
        if (!"admin".equals(tipoUsuario)) {
            return false;
        }

        // conferir dados administrativos rejeita um usuario que nao possui os dados de administrador
        if (!(usuarioLogado instanceof loginAdminDTO)) {
            return false;
        }

        // conferir email da sessao rejeita valores que nao sao texto
        if (!(emailSessao instanceof String)) {
            return false;
        }

        loginAdminDTO admin = (loginAdminDTO) usuarioLogado;

        // exigir email do administrador rejeita contas sem email preenchido
        if (admin.getEmail() == null || admin.getEmail().isBlank()) {
            return false;
        }

        return admin.getEmail().trim().equalsIgnoreCase(((String) emailSessao).trim());
    }
}
