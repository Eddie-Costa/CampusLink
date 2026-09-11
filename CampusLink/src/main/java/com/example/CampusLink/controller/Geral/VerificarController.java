package com.example.CampusLink.controller.Geral;

import com.example.CampusLink.dao.alunoDAO;
import com.example.CampusLink.dao.professorDAO;
import com.example.CampusLink.dao.usuarioDAO;
import com.example.CampusLink.dto.Aluno.cadastrarAlunoDTO;
import com.example.CampusLink.dto.Aluno.loginAlunoDTO;
import com.example.CampusLink.dto.Professor.cadastrarProfessorDTO;
import com.example.CampusLink.dto.Professor.loginProfessorDTO;
import com.example.CampusLink.exception.SQLErrorHandler;
import com.example.CampusLink.service.TwoFactorService;
import com.example.CampusLink.service.emailService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;

@Controller
public class VerificarController {

    private static final Logger logger = LoggerFactory.getLogger(VerificarController.class);

    @Autowired
    private TwoFactorService twoFactorService;

    @Autowired
    private emailService emailService;

    @Autowired
    private usuarioDAO usuarioDAO;

    @Autowired
    private alunoDAO alunoDAO;

    @Autowired
    private professorDAO professorDAO;

    @GetMapping("/verificarAluno")
    public String paginaVerificacaoAluno(HttpSession session, Model model) {

        if (!fluxoLoginValido(session, "aluno")) {
            return "redirect:/loginAluno";}

        String email = (String) session.getAttribute("email2FA");
        prepararModeloVerificacao(model, false, email);
        return "Geral/verificar";
    }

    @GetMapping("/verificarProfessor")
    public String paginaVerificacaoProfessor(HttpSession session, Model model) {

        if (!fluxoLoginValido(session, "professor")) {

            return "redirect:/loginProfessor";
        }

        String email = (String) session.getAttribute("email2FA");
        prepararModeloVerificacao(model, false, email);
        return "Geral/verificar";
    }

    @PostMapping("/verificar")
    public String verificarCodigoLogin(
            @RequestParam(
                    name = "codigo",
                    defaultValue = ""
            ) String codigo,
            HttpSession session,
            Model model
    ) throws SQLException {

        String email = (String) session.getAttribute("email2FA");
        String tipoUsuario = (String) session.getAttribute("tipoUsuario");
        if (!fluxoLoginValido(session, tipoUsuario)) {
            return redirecionarParaLogin(tipoUsuario);
        }
        long expiracaoCodigo = twoFactorService.obterExpiracaoCodigo(email);
        boolean codigoExpirado = expiracaoCodigo <= 0 || System.currentTimeMillis() >= expiracaoCodigo;
        boolean codigoValido = twoFactorService.validarCodigo(email, codigo.trim());

        if (!codigoValido) {

            prepararModeloVerificacao(model, false, email);
            model.addAttribute("erro", codigoExpirado ? "o codigo expirou solicite um novo codigo" : "codigo invalido tente novamente");
            return "Geral/verificar";
        }

        if ("aluno".equals(tipoUsuario)) {

            loginAlunoDTO aluno = alunoDAO.buscarPorEmailAluno(email);
            if (aluno == null) {
                limparLoginPendente(session, email);
                return "redirect:/loginAluno";
            }
            session.setAttribute("usuarioLogado", aluno);
        } else if ("professor".equals(tipoUsuario)) {

            loginProfessorDTO professor = professorDAO.buscarPorEmailProfessor(email);

            if (professor == null) {
                limparLoginPendente(session, email);
                return "redirect:/loginProfessor";
            }
            session.setAttribute("usuarioLogado", professor);
        }

        // mantem os dados usados pelas paginas autenticadas
        session.setAttribute("tipoUsuario", tipoUsuario);
        session.setAttribute("email2FA", email);
        session.setMaxInactiveInterval(900);
        limparLoginPendente(session, email);
        logger.info("login de {} concluido apos confirmacao do 2fa", tipoUsuario);
        return "redirect:/home";
    }

    @GetMapping("/verificarCadastro")
    public String paginaVerificacaoCadastro(HttpSession session, Model model) {

        Object cadastro = session.getAttribute("cadastroPendente");
        String chave = (String) session.getAttribute("chave2FACadastro");
        if (!cadastroPendenteValido(cadastro) || chave == null) {
            limparCadastroPendente(session);
            return redirecionarParaCadastro(cadastro);
        }
        prepararModeloVerificacao(model, true, chave);
        return "Geral/verificar";
    }

    @PostMapping("/verificarCadastro")
    public String verificarCodigoCadastro(
            @RequestParam(
                    name = "codigo",
                    defaultValue = ""
            ) String codigo,
            HttpSession session,
            Model model
    ) {

        synchronized (session) {
            Object cadastro = session.getAttribute("cadastroPendente");
            String chave = (String) session.getAttribute("chave2FACadastro");
            Integer tentativas = (Integer) session.getAttribute("tentativasCadastro2FA");
            if (!cadastroPendenteValido(cadastro) || chave == null) {
                limparCadastroPendente(session);
                return redirecionarParaCadastro(cadastro);
            }

            long expiracaoCodigo = twoFactorService.obterExpiracaoCodigo(chave);
            if (expiracaoCodigo <= 0 || System.currentTimeMillis() >= expiracaoCodigo) {

                prepararModeloVerificacao(
                        model,
                        true,
                        chave);
                model.addAttribute("erro", "o codigo expirou solicite um novo codigo");
                return "Geral/verificar";
            }

            int quantidadeTentativas = tentativas == null ? 0 : tentativas;

            if (quantidadeTentativas >= 5) {

                limparCadastroPendente(session);
                return voltarAoFormularioCadastro(cadastro, "limite de tentativas atingido inicie o cadastro novamente", model);
            }

            boolean codigoValido = twoFactorService.validarCodigo(chave, codigo.trim());

            if (!codigoValido) {

                quantidadeTentativas++;
                session.setAttribute("tentativasCadastro2FA", quantidadeTentativas);

                if (quantidadeTentativas >= 5) {
                    limparCadastroPendente(session);
                    return voltarAoFormularioCadastro(
                            cadastro,
                            "limite de tentativas atingido inicie o cadastro novamente",
                            model);
                }
                prepararModeloVerificacao(model, true, chave);
                model.addAttribute("erro", "codigo invalido tentativas restantes " + (5 - quantidadeTentativas));
                return "Geral/verificar";
            }
            limparCadastroPendente(session);
            String tipo;
            String identificador;
            String nome;
            String email;
            String telefone;
            String dataNasc;
            String senhaHash;

            if (cadastro instanceof cadastrarAlunoDTO aluno) {

                tipo = "Aluno";
                identificador = aluno.getRgm();
                nome = aluno.getNome();
                email = aluno.getEmail();
                telefone = aluno.getTelefone();
                dataNasc = aluno.getDataNasc();
                senhaHash = aluno.getSenha();
            } else {

                cadastrarProfessorDTO professor =
                        (cadastrarProfessorDTO) cadastro;
                tipo = "Professor";
                identificador = professor.getMatricula();
                nome = professor.getNome();
                email = professor.getEmail();
                telefone = professor.getTelefone();
                dataNasc = professor.getDataNasc();
                senhaHash = professor.getSenha();
            }

            try {

                List<String> erros =
                        usuarioDAO.validarDadosDuplicados(tipo, identificador, email, telefone);

                if (!erros.isEmpty()) {

                    SQLErrorHandler.VerificarErro(
                            tipo.toLowerCase(Locale.ROOT),
                            erros,
                            model);
                    return voltarAoFormularioCadastro(cadastro, null, model);
                }

                usuarioDAO.InsertCadastroUsuarioIntoBD(
                        tipo,
                        identificador,
                        nome.toLowerCase(Locale.ROOT),
                        email,
                        telefone,
                        dataNasc,
                        senhaHash);

                logger.info("cadastro de {} concluido apos confirmacao do 2fa", tipo);
                return "Aluno".equals(tipo) ? "redirect:/loginAluno" : "redirect:/loginProfessor";

            } catch (SQLException | RuntimeException e) {

                logger.error("erro ao concluir o cadastro apos o 2fa", e);
                return voltarAoFormularioCadastro(cadastro, "nao foi possivel concluir o cadastro verifique os dados e tente novamente", model);
            }
        }
    }

    @PostMapping("/reenviarCodigo")
    public String reenviarCodigo(HttpSession session, RedirectAttributes redirectAttributes) {

        synchronized (session) {
            Object cadastro = session.getAttribute("cadastroPendente");

            if (cadastroPendenteValido(cadastro)) {
                String chave =
                        (String) session.getAttribute("chave2FACadastro");

                if (chave == null) {
                    limparCadastroPendente(session);
                    return redirecionarParaCadastro(cadastro);
                }

                try {

                    String email = obterEmailCadastro(cadastro);
                    String novoCodigo = solicitarNovoCodigo(chave);
                    emailService.enviarCodigo(email, novoCodigo);
                    session.setAttribute("expiracaoCadastro2FA", twoFactorService.obterExpiracaoCodigo(chave));
                    session.setAttribute("tentativasCadastro2FA", 0);
                    redirectAttributes.addFlashAttribute("mensagem", "um novo codigo foi enviado para seu email");
                    return "redirect:/verificarCadastro";
                } catch (IllegalStateException e) {

                    long segundosRestantes = twoFactorService.segundosParaReenvio(chave);
                    redirectAttributes.addFlashAttribute("erro", segundosRestantes > 0 ? "aguarde " + segundosRestantes + " segundos para solicitar outro codigo" : "nao foi possivel gerar um novo codigo"
                    );
                    return "redirect:/verificarCadastro";
                } catch (RuntimeException e) {

                    logger.error("erro ao reenviar codigo do cadastro", e);
                    redirectAttributes.addFlashAttribute("erro", "nao foi possivel enviar o codigo tente novamente");
                    return "redirect:/verificarCadastro";
                }
            }

            String email = (String) session.getAttribute("email2FA");
            String tipoUsuario = (String) session.getAttribute("tipoUsuario");

            if (!fluxoLoginValido(session, tipoUsuario)) {
                return redirecionarParaLogin(tipoUsuario);
            }

            String rotaVerificacao = "aluno".equals(tipoUsuario) ? "redirect:/verificarAluno" : "redirect:/verificarProfessor";

            try {

                String novoCodigo = solicitarNovoCodigo(email);
                emailService.enviarCodigo(email, novoCodigo);
                session.setAttribute("expiracaoLogin2FA", twoFactorService.obterExpiracaoCodigo(email));
                redirectAttributes.addFlashAttribute("mensagem", "um novo codigo foi enviado para seu email");
                return rotaVerificacao;
            } catch (IllegalStateException e) {

                long segundosRestantes = twoFactorService.segundosParaReenvio(email);
                redirectAttributes.addFlashAttribute(
                        "erro",
                        segundosRestantes > 0 ? "aguarde " + segundosRestantes + " segundos para solicitar outro codigo" : "nao foi possivel gerar um novo codigo"
                );
                return rotaVerificacao;

            } catch (RuntimeException e) {

                logger.error("erro ao reenviar codigo do login", e);
                redirectAttributes.addFlashAttribute("erro", "nao foi possivel enviar o codigo tente novamente"
                );

                return rotaVerificacao;
            }
        }
    }

    private boolean fluxoLoginValido(HttpSession session, String tipoEsperado) {

        Object redirect = session.getAttribute("redirect");
        Object tipoUsuario = session.getAttribute("tipoUsuario");
        Object email = session.getAttribute("email2FA");
        return "Login".equals(redirect) && tipoEsperado != null && tipoEsperado.equals(tipoUsuario) && email instanceof String && !((String) email).isBlank();
    }

    private String redirecionarParaLogin(String tipoUsuario) {

        if ("aluno".equals(tipoUsuario)) {
            return "redirect:/loginAluno";
        }

        if ("professor".equals(tipoUsuario)) {
            return "redirect:/loginProfessor";
        }

        return "redirect:/login";
    }

    private boolean cadastroPendenteValido(Object cadastro) {

        return cadastro instanceof cadastrarAlunoDTO || cadastro instanceof cadastrarProfessorDTO;
    }

    private String redirecionarParaCadastro(Object cadastro) {

        if (cadastro instanceof cadastrarAlunoDTO) {
            return "redirect:/cadastrarAluno";
        }

        if (cadastro instanceof cadastrarProfessorDTO) {
            return "redirect:/cadastrarProfessor";
        }

        return "redirect:/cadastrar";
    }

    private String obterEmailCadastro(Object cadastro) {

        if (cadastro instanceof cadastrarAlunoDTO aluno) {
            return aluno.getEmail();
        }

        if (cadastro instanceof cadastrarProfessorDTO professor) {
            return professor.getEmail();
        }

        return null;
    }

    private String solicitarNovoCodigo(String chave) {

        try {

            return twoFactorService.reenviarCodigo(chave);

        } catch (IllegalStateException e) {

            if (twoFactorService.segundosParaReenvio(chave) > 0) {
                throw e;
            }

            return TwoFactorService.gerarCodigo(chave);
        }
    }

    private void prepararModeloVerificacao(Model model, boolean verificacaoCadastro, String chave) {

        long segundosParaReenvio = twoFactorService.segundosParaReenvio(chave);
        long segundosParaExpirarCodigo = twoFactorService.segundosParaExpirarCodigo(chave);
        model.addAttribute("verificacaoCadastro", verificacaoCadastro);
        model.addAttribute("segundosParaReenvio", segundosParaReenvio);
        model.addAttribute("segundosParaExpirarCodigo", segundosParaExpirarCodigo);
        model.addAttribute("codigoExpirado", segundosParaExpirarCodigo <= 0);
    }

    private void limparLoginPendente(HttpSession session, String email) {

        synchronized (session) {

            if (email != null) {
                twoFactorService.limparCodigo(email);
            }

            // mantem o email usado pelas paginas autenticadas
            session.setAttribute("email2FA", email);
            session.removeAttribute("redirect");
            session.removeAttribute("expiracaoLogin2FA");
        }
    }

    private void limparCadastroPendente(HttpSession session) {

        synchronized (session) {

            String chave = (String) session.getAttribute("chave2FACadastro");

            if (chave != null) {
                twoFactorService.limparCodigo(chave);
            }

            session.removeAttribute("cadastroPendente");
            session.removeAttribute("chave2FACadastro");
            session.removeAttribute("expiracaoCadastro2FA");
            session.removeAttribute("tentativasCadastro2FA");
        }
    }

    private String voltarAoFormularioCadastro(Object cadastro, String mensagem, Model model) {

        if (mensagem != null) {
            model.addAttribute("mensagemDeErro", mensagem);
        }

        if (cadastro instanceof cadastrarAlunoDTO aluno) {

            aluno.setSenha(null);
            model.addAttribute("aluno", aluno);
            return "Usuarios/Aluno/cadastrarAluno";
        }

        if (cadastro instanceof cadastrarProfessorDTO professor) {

            professor.setSenha(null);
            model.addAttribute("professor", professor);
            return "Usuarios/Professor/cadastrarProfessor";
        }

        return "redirect:/cadastrar";
    }
}