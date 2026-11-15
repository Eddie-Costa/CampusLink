// verificacao das telas de turmas confere acesso por participacao gestao pelo responsavel materiais e participantes
package com.example.CampusLink.controller.Geral;

import com.example.CampusLink.dto.ConteudoDTO;
import com.example.CampusLink.dto.TurmaDTO;
import com.example.CampusLink.repository.AlunoRepository;
import com.example.CampusLink.repository.ProfessorRepository;
import com.example.CampusLink.repository.TurmaRepository;
import com.example.CampusLink.repository.UsuarioRepository;
import com.example.CampusLink.service.ArquivoService;
import com.example.CampusLink.service.ConteudoService;
import com.example.CampusLink.service.DominioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// testes das telas de turmas esta classe simula a turma 1 do professor 4 com o aluno 7 matriculado
class TurmasControllerTests {

    private static final String PROFESSOR = "prof@teste.com";
    private static final String ALUNO = "aluno@teste.com";

    private ProfessorRepository professores;
    private AlunoRepository alunos;
    private TurmaRepository turmas;
    private ConteudoService conteudos;
    private ArquivoService arquivos;
    private turmasController controlador;
    private ExtendedModelMap modelo;
    private RedirectAttributesModelMap redirecionamento;

    // preparar testes das turmas este metodo cria o controlador com bancos e servicos simulados e a turma 1 do professor 4
    @BeforeEach
    void preparar() throws SQLException {
        professores = mock(ProfessorRepository.class);
        alunos = mock(AlunoRepository.class);
        turmas = mock(TurmaRepository.class);
        conteudos = mock(ConteudoService.class);
        arquivos = mock(ArquivoService.class);
        controlador = new turmasController();
        ReflectionTestUtils.setField(controlador, "UsuarioRepository", mock(UsuarioRepository.class));
        ReflectionTestUtils.setField(controlador, "professorRepository", professores);
        ReflectionTestUtils.setField(controlador, "alunoRepository", alunos);
        ReflectionTestUtils.setField(controlador, "turmaRepository", turmas);
        ReflectionTestUtils.setField(controlador, "conteudoService", conteudos);
        ReflectionTestUtils.setField(controlador, "arquivoService", arquivos);
        ReflectionTestUtils.setField(controlador, "dominioService", mock(DominioService.class));
        modelo = new ExtendedModelMap();
        redirecionamento = new RedirectAttributesModelMap();
        TurmaDTO turma = turma(1L, "Calculo", 4L);
        when(professores.buscarPorIDProfessor(PROFESSOR)).thenReturn("4");
        when(alunos.buscarPorIDAluno(ALUNO)).thenReturn("7");
        when(turmas.buscarTurmasDoUsuario("professor", "4")).thenReturn(List.of(turma));
        when(turmas.buscarTurmasDoUsuario("aluno", "7")).thenReturn(List.of(turma));
        when(turmas.buscarTurmaPorId("1")).thenReturn(turma);
        when(turmas.buscarParticipantesDaTurma("1")).thenReturn(List.of(
                pessoa(PROFESSOR, "Professor Admin"), pessoa(ALUNO, "Aluno"), pessoa("colega@teste.com", "Professor")));
    }

    // testar lista de turmas este teste confere a lista do aluno do professor e o pedido sem login
    @Test
    void listaTurmasDoUsuarioLogado() throws SQLException {
        // preparar sessoes do aluno e do professor

        // executar listagens
        String telaAluno = controlador.Turmas(modelo, sessao(ALUNO, "aluno"));
        String telaProfessor = controlador.Turmas(new ExtendedModelMap(), sessao(PROFESSOR, "professor"));
        String semLogin = controlador.Turmas(modelo, new MockHttpSession());

        // verificar telas e consulta das turmas recentes
        assertEquals("Geral/turmas", telaAluno);
        assertEquals("Geral/turmas", telaProfessor);
        assertEquals("redirect:/login", semLogin);
        assertEquals(1, ((List<?>) modelo.get("turmas")).size());
        verify(turmas, org.mockito.Mockito.times(2)).recentesDentre(any(), any(), org.mockito.ArgumentMatchers.eq(5));
    }

    // testar ambiente do professor responsavel este teste confere permissoes e miniaturas dos videos e livros
    @Test
    @SuppressWarnings("unchecked")
    void abreAmbienteDoProfessorResponsavelComMidias() throws SQLException {
        // preparar materiais do youtube do google books e links invalidos
        when(conteudos.buscarPorTurma(1L, "  calc ", "video")).thenReturn(List.of(
                link(10L, "https://www.youtube.com/watch?v=abcdefghijk"), link(11L, "https://youtu.be/ABCDEFGHIJK"),
                link(12L, "https://youtube.com/shorts/zyxwvutsrqp"), link(13L, "https://books.google.com/books?id=livro1"),
                link(14L, "https://books.google.com/books/edition/_/abc_123"), link(15L, "https://play.google.com/store/books/details?id=xyz"),
                link(16L, "https://www.youtube.com/watch?v=curto"), link(17L, "nao e url"), link(18L, null), link(19L, "mailto:a@b.com")));

        // executar abertura do ambiente com busca
        String tela = controlador.ambienteTurma("1", "  calc ", "video", modelo, sessao(PROFESSOR, "professor"), redirecionamento);

        // verificar permissoes busca limpa e midias reconhecidas
        assertEquals("Geral/ambienteTurma", tela);
        assertEquals(true, modelo.get("isTurmaAdmin"));
        assertEquals(true, modelo.get("isProfessorTurma"));
        assertEquals("calc", modelo.get("termoBusca"));
        Map<Long, String> videos = (Map<Long, String>) modelo.get("videoIdsPorConteudo");
        Map<Long, String> livros = (Map<Long, String>) modelo.get("livroIdsPorConteudo");
        assertEquals(Map.of(10L, "abcdefghijk", 11L, "ABCDEFGHIJK", 12L, "zyxwvutsrqp"), videos);
        assertEquals(Map.of(13L, "livro1", 14L, "abc_123", 15L, "xyz"), livros);
        assertEquals("Vídeo do YouTube", ((Map<Long, String>) modelo.get("tiposMidiaPorConteudo")).get(10L));
    }

    // testar ambiente do aluno este teste confere os materiais concluidos e que o aluno nao gerencia a turma
    @Test
    void abreAmbienteDoAlunoComConcluidos() throws SQLException {
        // preparar material concluido
        when(conteudos.listarConcluidosDoAluno(7L, 1L)).thenReturn(Set.of(10L));

        // executar abertura
        String tela = controlador.ambienteTurma("1", null, null, modelo, sessao(ALUNO, "aluno"), redirecionamento);

        // verificar concluidos e permissoes
        assertEquals("Geral/ambienteTurma", tela);
        assertEquals(Set.of(10L), modelo.get("conteudosConcluidos"));
        assertEquals(false, modelo.get("isTurmaAdmin"));
        assertEquals(false, modelo.get("isProfessorTurma"));
        assertEquals("", modelo.get("termoBusca"));
    }

    // testar acesso a turma alheia este teste confere que o codigo invalido e a turma de outra pessoa voltam para a lista
    @Test
    void bloqueiaAmbienteDeTurmaAlheiaOuInvalida() throws SQLException {
        // preparar turma 2 da qual o aluno nao participa

        // executar aberturas invalidas
        String invalida = controlador.ambienteTurma("abc", null, null, modelo, sessao(ALUNO, "aluno"), redirecionamento);
        String alheia = controlador.ambienteTurma("2", null, null, modelo, sessao(ALUNO, "aluno"), redirecionamento);
        String semLogin = controlador.ambienteTurma("1", null, null, modelo, new MockHttpSession(), redirecionamento);

        // verificar redirecionamentos e aviso
        assertEquals("redirect:/turmas", invalida);
        assertEquals("redirect:/turmas", alheia);
        assertEquals("redirect:/login", semLogin);
        assertEquals("Você não participa desta turma.", redirecionamento.getFlashAttributes().get("mensagemErro"));
        verify(conteudos, never()).buscarPorTurma(any(), any(), any());
    }

    // testar sugestoes de pessoas este teste confere que so professores da turma buscam pessoas e com pelo menos duas letras
    @Test
    void sugerePessoasApenasParaProfessoresDaTurma() throws SQLException {
        // preparar sugestao do banco
        when(turmas.buscarSugestoesPessoasDaTurma("bi", "1")).thenReturn(List.of(pessoa("bia@teste.com", "Aluno")));

        // executar buscas
        List<TurmaDTO> sugestoes = controlador.sugestoesPessoas("1", " bi ", sessao("colega@teste.com", "professor"));
        List<TurmaDTO> curta = controlador.sugestoesPessoas("1", "b", sessao(PROFESSOR, "professor"));
        assertThrows(ResponseStatusException.class, () -> controlador.sugestoesPessoas("1", "bi", sessao(ALUNO, "aluno")));
        assertThrows(ResponseStatusException.class, () -> controlador.sugestoesPessoas("1", "bi", sessao("fora@teste.com", "professor")));

        // verificar sugestoes
        assertEquals(1, sugestoes.size());
        assertTrue(curta.isEmpty());
    }

    // testar edicao da turma este teste confere a edicao pelo responsavel e as recusas de outros usuarios
    @Test
    void editaTurmaSoPeloResponsavel() throws SQLException {
        // preparar edicao valida e professor que nao e o dono
        when(turmas.atualizarTurmaDoProprietario("1", "4", "Calculo II", "Noite")).thenReturn(true);
        when(professores.buscarPorIDProfessor("colega@teste.com")).thenReturn("5");
        TurmaDTO edicao = turma(null, "  Calculo II ", null);
        edicao.setDescricao("Noite");

        // executar edicoes
        String responsavel = controlador.editarTurma("1", edicao, semErros(edicao), sessao(PROFESSOR, "professor"), modelo, new TurmaDTO(), redirecionamento);
        String sucesso = (String) redirecionamento.getFlashAttributes().get("mensagemSucesso");
        String aluno = controlador.editarTurma("1", edicao, semErros(edicao), sessao(ALUNO, "aluno"), modelo, new TurmaDTO(), redirecionamento);
        String colega = controlador.editarTurma("1", edicao, semErros(edicao), sessao("colega@teste.com", "professor"), modelo, new TurmaDTO(), redirecionamento);
        String inexistente = controlador.editarTurma("9", edicao, semErros(edicao), sessao(PROFESSOR, "professor"), modelo, new TurmaDTO(), redirecionamento);

        // verificar resultado e recusas
        assertEquals("redirect:/turmas/1", responsavel);
        assertEquals("Turma atualizada com sucesso.", sucesso);
        assertEquals("redirect:/turmas/1", aluno);
        assertEquals("redirect:/turmas/1", colega);
        assertEquals("redirect:/turmas", inexistente);
        verify(turmas).atualizarTurmaDoProprietario("1", "4", "Calculo II", "Noite");
    }

    // testar edicao com erro este teste confere a tela reaberta com o formulario e a falha ao gravar
    @Test
    void reabreFormularioQuandoEdicaoFalha() throws SQLException {
        // preparar formulario com erro e gravacao recusada
        TurmaDTO edicao = turma(null, "", null);
        BindingResult comErro = new BeanPropertyBindingResult(edicao, "turmaEdicao");
        comErro.rejectValue("nomeTurma", "obrigatorio", "Informe o nome");
        TurmaDTO formulario = new TurmaDTO();
        formulario.setId(1L);

        // executar edicao com erro e edicao recusada pelo banco
        String tela = controlador.editarTurma("1", edicao, comErro, sessao(PROFESSOR, "professor"), modelo, formulario, redirecionamento);
        String recusada = controlador.editarTurma("1", turma(null, "Novo", null), semErros(edicao), sessao(PROFESSOR, "professor"), new ExtendedModelMap(), formulario, redirecionamento);

        // verificar tela com modal aberto e aviso de falha
        assertEquals("Geral/ambienteTurma", tela);
        assertEquals(true, modelo.get("abrirModalEditarTurma"));
        assertEquals("redirect:/turmas/1", recusada);
        assertEquals("Não foi possível atualizar os dados da turma.", redirecionamento.getFlashAttributes().get("mensagemErro"));
    }

    // testar novo material este teste confere o material ligado a turma e ao professor e a atividade registrada
    @Test
    void criaMaterialNaTurma() throws SQLException {
        // preparar material novo
        ConteudoDTO material = link(null, "https://site.com");

        // executar cadastro
        String tela = controlador.criarConteudo(1L, material, semErros(material), null, sessao(PROFESSOR, "professor"), modelo, redirecionamento);

        // verificar material e atividade
        assertEquals("redirect:/turmas/1", tela);
        assertEquals(1L, material.getIdTurma());
        assertEquals(4L, material.getIdProfessor());
        verify(conteudos).criar(material, null);
        verify(turmas).registrarAlteracao(PROFESSOR, 1L);
    }

    // testar novo material invalido este teste confere formulario com erro material recusado aluno e professor de fora
    @Test
    void reabreFormularioDeMaterialInvalido() throws SQLException {
        // preparar formulario com erro e material recusado pelo servico
        ConteudoDTO material = link(null, "https://site.com");
        BindingResult comErro = new BeanPropertyBindingResult(material, "conteudo");
        comErro.reject("titulo", "Informe o titulo");
        doThrow(new IllegalArgumentException("Domínio não autorizado")).when(conteudos).criar(any(), any());
        when(conteudos.listarPorTurma(1L)).thenReturn(List.of(link(10L, "https://youtu.be/ABCDEFGHIJK")));

        // executar cadastros invalidos
        String comErroTela = controlador.criarConteudo(1L, material, comErro, null, sessao(PROFESSOR, "professor"), modelo, redirecionamento);
        ExtendedModelMap modeloRecusa = new ExtendedModelMap();
        String recusado = controlador.criarConteudo(1L, material, semErros(material), null, sessao(PROFESSOR, "professor"), modeloRecusa, redirecionamento);
        String aluno = controlador.criarConteudo(1L, material, semErros(material), null, sessao(ALUNO, "aluno"), modelo, redirecionamento);
        String deFora = controlador.criarConteudo(2L, material, semErros(material), null, sessao(PROFESSOR, "professor"), modelo, redirecionamento);

        // verificar telas e mensagens
        assertEquals("Geral/ambienteTurma", comErroTela);
        assertEquals(true, modelo.get("abrirModalAdicionarConteudo"));
        assertEquals("Geral/ambienteTurma", recusado);
        assertEquals("Domínio não autorizado", modeloRecusa.get("mensagemErro"));
        assertEquals("redirect:/turmas/1", aluno);
        assertEquals("redirect:/turmas", deFora);
    }

    // testar nova turma este teste confere a turma criada com o professor vinculado e as recusas
    @Test
    void criaTurmaParaProfessor() throws SQLException {
        // preparar nova turma
        TurmaDTO nova = turma(null, "Fisica", null);
        when(turmas.buscarUltimaTurmaPorProfessor("4")).thenReturn("2");
        BindingResult comErro = new BeanPropertyBindingResult(nova, "turma");
        comErro.reject("nome", "Informe o nome");
        when(professores.buscarPorIDProfessor("sem@teste.com")).thenReturn("");

        // executar cadastros
        String criada = controlador.criarTurma(nova, semErros(nova), sessao(PROFESSOR, "professor"), modelo, redirecionamento);
        String invalida = controlador.criarTurma(nova, comErro, sessao(PROFESSOR, "professor"), modelo, redirecionamento);
        String aluno = controlador.criarTurma(nova, semErros(nova), sessao(ALUNO, "aluno"), modelo, redirecionamento);
        String semCadastro = controlador.criarTurma(nova, semErros(nova), sessao("sem@teste.com", "professor"), modelo, redirecionamento);

        // verificar gravacao e recusas
        assertEquals("redirect:/turmas", criada);
        verify(professores).InsertTurmasIntoBD("Fisica", null, "4");
        verify(professores).InsertProfessor_TurmaIntoBD("4", "2");
        assertEquals("Geral/turmas", invalida);
        assertEquals("redirect:/turmas", aluno);
        assertEquals("redirect:/turmas", semCadastro);
        assertEquals("Não foi possível identificar o professor.", redirecionamento.getFlashAttributes().get("mensagemErro"));
    }

    // testar acoes sobre materiais este teste confere exclusao material de outra turma e reabertura
    @Test
    void excluiEReabreMateriaisDaTurma() throws SQLException {
        // preparar material 10 da turma e material 20 de outra turma
        when(conteudos.pertenceATurma(10L, 1L)).thenReturn(true);
        when(conteudos.reabrir(10L, 4L, 1L)).thenReturn(true);

        // executar acoes
        controlador.excluirConteudo(1L, 10L, sessao(PROFESSOR, "professor"), redirecionamento);
        controlador.excluirConteudo(1L, 20L, sessao(PROFESSOR, "professor"), redirecionamento);
        String erroOutraTurma = (String) redirecionamento.getFlashAttributes().get("mensagemErro");
        controlador.reDisponibilizarConteudo(1L, 10L, sessao(PROFESSOR, "professor"), redirecionamento);
        String reaberto = (String) redirecionamento.getFlashAttributes().get("mensagemSucesso");
        controlador.reDisponibilizarConteudo(1L, 20L, sessao(PROFESSOR, "professor"), redirecionamento);
        String aluno = controlador.excluirConteudo(1L, 10L, sessao(ALUNO, "aluno"), redirecionamento);

        // verificar exclusao unica e mensagens
        verify(conteudos).excluir(10L);
        verify(conteudos, never()).excluir(20L);
        assertEquals("Este conteúdo não pertence a esta turma.", erroOutraTurma);
        assertTrue(reaberto.contains("disponível novamente"));
        assertEquals("Não foi possível re-disponibilizar este conteúdo.", redirecionamento.getFlashAttributes().get("mensagemErro"));
        assertEquals("redirect:/turmas/1", aluno);
    }

    // testar conclusao pelo aluno este teste confere conclusao desfazer pela agenda prazo vencido e turma alheia
    @Test
    void alunoConcluiMaterialDaPropriaTurma() throws SQLException {
        // preparar material 11 com prazo vencido
        doThrow(new IllegalArgumentException("O prazo deste conteúdo terminou")).when(conteudos).alterarConclusao(7L, 1L, 11L, true);

        // executar conclusoes
        String concluido = controlador.concluirConteudo(1L, 10L, true, null, sessao(ALUNO, "aluno"), redirecionamento);
        String desfeito = controlador.concluirConteudo(1L, 10L, false, "eventos", sessao(ALUNO, "aluno"), redirecionamento);
        String mensagemDesfeito = (String) redirecionamento.getFlashAttributes().get("mensagem");
        String vencido = controlador.concluirConteudo(1L, 11L, true, "eventos", sessao(ALUNO, "aluno"), redirecionamento);
        String alheia = controlador.concluirConteudo(2L, 10L, true, null, sessao(ALUNO, "aluno"), redirecionamento);
        String professor = controlador.concluirConteudo(1L, 10L, true, null, sessao(PROFESSOR, "professor"), redirecionamento);

        // verificar destinos e mensagens
        assertEquals("redirect:/turmas/1", concluido);
        assertEquals("redirect:/eventos", desfeito);
        assertEquals("O conteúdo voltou a ficar pendente.", mensagemDesfeito);
        assertEquals("redirect:/eventos", vencido);
        assertEquals("O prazo deste conteúdo terminou", redirecionamento.getFlashAttributes().get("erro"));
        assertEquals("redirect:/turmas", alheia);
        assertEquals("redirect:/turmas/1", professor);
        verify(conteudos, never()).alterarConclusao(anyLong(), org.mockito.ArgumentMatchers.eq(2L), anyLong(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    // testar edicao de material este teste confere edicao com novo arquivo formulario com erro e material recusado
    @Test
    void editaMaterialComArquivoOuReabreFormulario() throws SQLException {
        // preparar material 10 da turma e novo arquivo
        when(conteudos.pertenceATurma(10L, 1L)).thenReturn(true);
        ConteudoDTO material = link(null, "https://site.com");
        MockMultipartFile pdf = new MockMultipartFile("arquivo", "aula.pdf", "application/pdf", new byte[]{1});
        BindingResult comErro = new BeanPropertyBindingResult(material, "conteudoEditado");
        comErro.reject("titulo", "Informe o titulo");

        // executar edicoes
        String editado = controlador.editarConteudo(1L, 10L, material, semErros(material), pdf, sessao(PROFESSOR, "professor"), modelo, redirecionamento);
        String invalido = controlador.editarConteudo(1L, 10L, material, comErro, null, sessao(PROFESSOR, "professor"), modelo, redirecionamento);
        String erroValidacao = (String) modelo.get("erroEditar");
        doThrow(new IllegalArgumentException("Link inválido")).when(conteudos).atualizar(any());
        ExtendedModelMap modeloRecusa = new ExtendedModelMap();
        String recusado = controlador.editarConteudo(1L, 10L, material, semErros(material), null, sessao(PROFESSOR, "professor"), modeloRecusa, redirecionamento);
        String outraTurma = controlador.editarConteudo(1L, 20L, material, semErros(material), null, sessao(PROFESSOR, "professor"), modelo, redirecionamento);

        // verificar gravacoes e telas
        assertEquals("redirect:/turmas/1", editado);
        verify(arquivos).salvar(pdf, 10L);
        assertEquals("Geral/ambienteTurma", invalido);
        assertEquals("Informe o titulo", erroValidacao);
        assertEquals("Geral/ambienteTurma", recusado);
        assertEquals("Link inválido", modeloRecusa.get("erroEditar"));
        assertEquals("redirect:/turmas/1", outraTurma);
    }

    // testar entrada de pessoas este teste confere inclusao falha email vazio professor de fora e aluno
    @Test
    void adicionaPessoasPelaTurma() throws SQLException {
        // preparar inclusao aceita para bia e recusada para outro email
        when(turmas.inserirPessoaTurma("bia@teste.com", "1")).thenReturn(true);

        // executar inclusoes
        ExtendedModelMap modeloSucesso = new ExtendedModelMap();
        controlador.adicionarPessoas(pedido(1L, " bia@teste.com "), sessao(PROFESSOR, "professor"), modeloSucesso, redirecionamento);
        ExtendedModelMap modeloFalha = new ExtendedModelMap();
        controlador.adicionarPessoas(pedido(1L, "nada@teste.com"), sessao(PROFESSOR, "professor"), modeloFalha, redirecionamento);
        String vazio = controlador.adicionarPessoas(pedido(1L, " "), sessao(PROFESSOR, "professor"), new ExtendedModelMap(), redirecionamento);
        String deFora = controlador.adicionarPessoas(pedido(1L, "x@teste.com"), sessao("fora@teste.com", "professor"), modelo, redirecionamento);
        String aluno = controlador.adicionarPessoas(pedido(1L, "x@teste.com"), sessao(ALUNO, "aluno"), modelo, redirecionamento);
        String semTurma = controlador.adicionarPessoas(pedido(null, "x@teste.com"), sessao(PROFESSOR, "professor"), modelo, redirecionamento);

        // verificar mensagens e destinos
        assertEquals("Pessoa adicionada com sucesso.", modeloSucesso.get("mensagemSucesso"));
        assertEquals("Não foi possível adicionar a pessoa informada.", modeloFalha.get("mensagemErro"));
        assertEquals("Geral/ambienteTurma", vazio);
        assertEquals("redirect:/turmas/1", deFora);
        assertEquals("Geral/home", aluno);
        assertEquals("redirect:/turmas", semTurma);
    }

    // testar saida de pessoas este teste confere remocao protecao do professor administrador e recusas
    @Test
    void removePessoasProtegendoProfessorAdministrador() throws SQLException {
        // preparar remocao aceita para o aluno
        when(turmas.revomerPessoaTurma(ALUNO, "1")).thenReturn(true);

        // executar remocoes
        controlador.removerPessoas(pedido(1L, " " + ALUNO + " "), sessao(PROFESSOR, "professor"), redirecionamento);
        String removido = (String) redirecionamento.getFlashAttributes().get("mensagemSucesso");
        controlador.removerPessoas(pedido(1L, PROFESSOR), sessao("colega@teste.com", "professor"), redirecionamento);
        String protegido = (String) redirecionamento.getFlashAttributes().get("mensagemErro");
        controlador.removerPessoas(pedido(1L, "colega@teste.com"), sessao(PROFESSOR, "professor"), redirecionamento);
        String falha = (String) redirecionamento.getFlashAttributes().get("mensagemErro");
        controlador.removerPessoas(pedido(1L, " "), sessao(PROFESSOR, "professor"), redirecionamento);
        String vazio = (String) redirecionamento.getFlashAttributes().get("mensagemErro");
        String aluno = controlador.removerPessoas(pedido(1L, PROFESSOR), sessao(ALUNO, "aluno"), redirecionamento);
        String deFora = controlador.removerPessoas(pedido(1L, ALUNO), sessao("fora@teste.com", "professor"), redirecionamento);
        String semTurma = controlador.removerPessoas(pedido(null, ALUNO), sessao(PROFESSOR, "professor"), redirecionamento);

        // verificar mensagens e que o administrador nao foi removido
        assertEquals("Participante removido com sucesso.", removido);
        assertEquals("O professor administrador da turma não pode ser removido.", protegido);
        assertEquals("Não foi possível remover este participante.", falha);
        assertEquals("Selecione um participante válido.", vazio);
        assertEquals("Geral/home", aluno);
        assertEquals("redirect:/turmas/1", deFora);
        assertEquals("redirect:/turmas", semTurma);
        verify(turmas, never()).revomerPessoaTurma(PROFESSOR, "1");
    }

    // testar exclusao da turma e arquivo grande este teste confere a exclusao pelo professor e o aviso de limite do arquivo
    @Test
    void excluiTurmaEAvisaArquivoGrande() throws SQLException {
        // preparar pedido de exclusao e envio grande
        MockHttpServletRequest envio = new MockHttpServletRequest("POST", "/turmas/1/conteudos");

        // executar exclusao recusa do aluno e tratamento do arquivo grande
        String excluida = controlador.excluirTurma(pedido(1L, null), sessao(PROFESSOR, "professor"), modelo, redirecionamento);
        String aluno = controlador.excluirTurma(pedido(1L, null), sessao(ALUNO, "aluno"), modelo, redirecionamento);
        String grande = controlador.tratarArquivoMuitoGrande(envio, redirecionamento);

        // verificar exclusao e mensagens
        assertEquals("redirect:/turmas", excluida);
        verify(turmas).excluirTurma("1", "4");
        assertEquals("Geral/home", aluno);
        assertEquals("redirect:/turmas/1", grande);
        assertTrue(redirecionamento.getFlashAttributes().get("mensagemErro").toString().contains("50 MB"));
    }

    // montar sessao este metodo cria a sessao logada com email e perfil
    private MockHttpSession sessao(String email, String tipo) {
        MockHttpSession sessao = new MockHttpSession();
        sessao.setAttribute("usuarioLogado", new Object());
        sessao.setAttribute("email2FA", email);
        sessao.setAttribute("tipoUsuario", tipo);
        return sessao;
    }

    // montar formulario sem erros este metodo cria o resultado de validacao vazio
    private BindingResult semErros(Object alvo) {
        return new BeanPropertyBindingResult(alvo, "formulario");
    }

    // montar turma este metodo cria a turma com codigo nome e responsavel
    private TurmaDTO turma(Long id, String nome, Long idProprietario) {
        TurmaDTO turma = new TurmaDTO();
        turma.setId(id);
        turma.setNomeTurma(nome);
        turma.setIdProprietario(idProprietario);
        return turma;
    }

    // montar participante este metodo cria a pessoa da turma com email e perfil
    private TurmaDTO pessoa(String email, String tipo) {
        TurmaDTO pessoa = new TurmaDTO();
        pessoa.setEmailPessoa(email);
        pessoa.setTipoUsuario(tipo);
        return pessoa;
    }

    // montar pedido de participante este metodo cria o formulario com turma e email da pessoa
    private TurmaDTO pedido(Long idTurma, String email) {
        TurmaDTO pedido = new TurmaDTO();
        pedido.setId(idTurma);
        pedido.setEmailPessoa(email);
        return pedido;
    }

    // montar material este metodo cria um material com codigo e link
    private ConteudoDTO link(Long id, String url) {
        ConteudoDTO conteudo = new ConteudoDTO();
        conteudo.setId(id);
        conteudo.setTitulo("Material");
        conteudo.setUrl(url);
        return conteudo;
    }
}
