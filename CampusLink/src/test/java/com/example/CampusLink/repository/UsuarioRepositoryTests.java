// verificacao do banco de usuarios confere cadastro login anonimizacao duplicidade e registro de logs
package com.example.CampusLink.repository;

import com.example.CampusLink.dto.DadosUsuarioDTO;
import com.example.CampusLink.service.SupabaseStorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.example.CampusLink.repository.BancoSimulado.linha;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

// testes do banco de usuarios esta classe usa o banco simulado e um armazenamento de arquivos falso
class UsuarioRepositoryTests {

    private static final String CONTA_PARA_ANONIMIZAR = "AND LOWER(email) = ? FOR UPDATE";
    private static final String ADMINISTRADOR = "SELECT master FROM";
    private static final String SESSAO_LOG = "SELECT fim FROM";

    private BancoSimulado banco;
    private SupabaseStorageService storage;
    private UsuarioRepository usuarios;

    // preparar testes dos usuarios este metodo liga o repositorio ao banco e ao armazenamento simulados
    @BeforeEach
    void preparar() {
        banco = new BancoSimulado();
        storage = mock(SupabaseStorageService.class);
        usuarios = new UsuarioRepository();
        ReflectionTestUtils.setField(usuarios, "dataSource", banco.dataSource());
        ReflectionTestUtils.setField(usuarios, "storage", storage);
    }

    // limpar sessao de log este metodo remove a sessao colocada no contexto dos logs
    @AfterEach
    void limpar() {
        MDC.remove("sessaoLogId");
    }

    // testar dados do titular este teste confere a busca pelo email normalizado e a conta inexistente
    @Test
    void consultaDadosPessoaisPeloEmailNormalizado() throws SQLException {
        // preparar conta ativa
        banco.quandoConsultar("AS identificador", linha("id", 2L, "nome", "Ana", "email", "ana@teste.com",
                "datanasc", LocalDate.of(2004, 3, 1), "perfil", "ALUNOS", "identificador", "123", "created_at", LocalDate.of(2026, 1, 5)));

        // executar consulta com email em maiusculas e espacos e depois conta inexistente
        DadosUsuarioDTO dados = usuarios.consultarDadosPessoais("  Ana@Teste.COM ");
        banco.semResultado("AS identificador");
        DadosUsuarioDTO inexistente = usuarios.consultarDadosPessoais("nada@teste.com");

        // verificar dados e email enviado
        assertEquals(LocalDate.of(2004, 3, 1), dados.getDataNascimento());
        assertEquals("123", dados.getIdentificador());
        assertEquals(List.of("ana@teste.com"), banco.comandos().get(0).valores());
        assertNull(inexistente);
    }

    // testar vinculos do professor este teste confere a resposta do banco sobre turmas conteudos ou eventos
    @Test
    void informaSeContaPossuiVinculos() throws SQLException {
        // preparar professor com material
        banco.quandoConsultar("SELECT EXISTS", linha("exists", true));

        // executar conferencias
        boolean comVinculos = usuarios.possuiVinculosParaExclusao(6L);
        banco.quandoConsultar("SELECT EXISTS", linha("exists", false));
        boolean semVinculos = usuarios.possuiVinculosParaExclusao(7L);

        // verificar respostas
        assertTrue(comVinculos);
        assertFalse(semVinculos);
    }

    // testar anonimizacao da conta este teste confere que materiais removidos sao apagados e os dados pessoais trocados
    @Test
    void anonimizaContaApagandoMateriaisRemovidos() throws SQLException {
        // preparar professor sem cargo administrativo com um material removido e um arquivo
        banco.semResultado(ADMINISTRADOR);
        banco.quandoConsultar("c.status = 'removido' FOR UPDATE OF c", linha("id", 40L));
        banco.quandoConsultar("SELECT bucket_id, storage_path", linha("bucket_id", "materiais", "storage_path", "40/aula.pdf"));

        // executar anonimizacao
        usuarios.anonimizarDadosPessoais(6L, " Prof@Teste.com ");

        // verificar arquivo apagado consentimento revogado logs mascarados e dados trocados
        verify(storage).excluir("materiais", "40/aula.pdf");
        assertTrue(banco.executou("DELETE FROM public.\"CONTEUDOS\" WHERE id = ?"));
        assertTrue(banco.executou("SET revogado_em = now()"));
        assertEquals("***=prof@teste.com", banco.ultimo("UPDATE public.\"LOGS_EVENTOS\"").valores().get(0));
        assertEquals("%prof@teste.com%", banco.ultimo("UPDATE public.\"LOGS_SESSOES\"").valores().get(1));
        assertTrue(banco.ultimo("SET nome = 'Usuário removido'").sql().contains("anonimizado_em = now()"));
        assertEquals(1, banco.commits());
    }

    // testar conta inexistente este teste confere que nada e alterado quando o email nao pertence a conta
    @Test
    void desfazAnonimizacaoQuandoContaNaoConfere() {
        // preparar conta nao encontrada
        banco.semResultado(CONTA_PARA_ANONIMIZAR);

        // executar anonimizacao
        IllegalStateException erro = assertThrows(IllegalStateException.class,
                () -> usuarios.anonimizarDadosPessoais(6L, "outro@teste.com"));

        // verificar mensagem desfazimento e nenhum dado trocado
        assertEquals("Conta não encontrada.", erro.getMessage());
        assertEquals(1, banco.rollbacks());
        assertFalse(banco.executou("SET nome = 'Usuário removido'"));
    }

    // testar administrador se anonimizando este teste confere que administradores nao encerram a propria conta
    @Test
    void impedeAdministradorDeEncerrarPropriaConta() {
        // preparar administrador master
        banco.quandoConsultar(ADMINISTRADOR, linha("master", true));

        // executar tentativas do master e de administrador comum
        IllegalStateException master = assertThrows(IllegalStateException.class,
                () -> usuarios.anonimizarDadosPessoais(1L, "master@teste.com"));
        banco.quandoConsultar(ADMINISTRADOR, linha("master", false));
        IllegalStateException comum = assertThrows(IllegalStateException.class,
                () -> usuarios.anonimizarDadosPessoais(2L, "admin@teste.com"));

        // verificar mensagens e desfazimento das duas tentativas
        assertTrue(master.getMessage().contains("outro administrador master"));
        assertTrue(comum.getMessage().contains("página de gerenciamento"));
        assertEquals(2, banco.rollbacks());
    }

    // testar cadastro do aluno este teste confere usuario rgm e aceite dos termos na mesma transacao
    @Test
    void cadastraAlunoComAceiteDosTermos() throws SQLException {
        // preparar codigo gerado para o usuario
        banco.chaveGerada(30L);

        // executar cadastro
        usuarios.InsertCadastroUsuarioIntoBD("Aluno", "123", "Ana", " Ana@Teste.com ", "11999999999", "2004-03-01", "hash");

        // verificar email normalizado perfil rgm aceite e confirmacao
        List<Object> usuario = banco.ultimo("INSERT INTO public.\"USUARIOS\"").valores();
        assertEquals("ana@teste.com", usuario.get(1));
        assertEquals("ALUNOS", usuario.get(5));
        assertEquals(List.of(30L, "123"), banco.ultimo("INSERT INTO public.\"ALUNOS\" (\"id_usuario\", \"rgm\")").valores());
        assertEquals(List.of(30L), banco.ultimo("INSERT INTO public.\"CONSENTIMENTOS\"").valores());
        assertEquals(1, banco.commits());
    }

    // testar cadastro do professor este teste confere a matricula gravada na tabela de professores
    @Test
    void cadastraProfessorComMatricula() throws SQLException {
        // preparar codigo gerado para o usuario
        banco.chaveGerada(31L);

        // executar cadastro
        usuarios.InsertCadastroUsuarioIntoBD("professor", "P1", "Bia", "bia@teste.com", "11988888888", "1990-05-10", "hash");

        // verificar matricula
        assertEquals(List.of(31L, "P1"), banco.ultimo("INSERT INTO public.\"PROFESSORES\" (\"id_usuario\", \"matricula\")").valores());
    }

    // testar cadastro invalido este teste confere perfil invalido falta de termos e usuario nao gravado
    @Test
    void recusaCadastroSemPerfilValidoOuSemTermos() {
        // preparar banco sem versao dos termos
        banco.quandoAlterar("INSERT INTO public.\"CONSENTIMENTOS\"", 0);

        // executar cadastros invalidos
        assertThrows(IllegalArgumentException.class,
                () -> usuarios.InsertCadastroUsuarioIntoBD("Admin", "1", "X", "x@teste.com", "1", "2000-01-01", "h"));
        SQLException semTermos = assertThrows(SQLException.class,
                () -> usuarios.InsertCadastroUsuarioIntoBD("Aluno", "1", "X", "x@teste.com", "1", "2000-01-01", "h"));
        banco.quandoAlterar("INSERT INTO public.\"USUARIOS\"", 0);
        SQLException semUsuario = assertThrows(SQLException.class,
                () -> usuarios.InsertCadastroUsuarioIntoBD("Aluno", "1", "X", "x@teste.com", "1", "2000-01-01", "h"));

        // verificar mensagens e nenhuma confirmacao
        assertTrue(semTermos.getMessage().contains("termos"));
        assertTrue(semUsuario.getMessage().contains("Nenhum usuário"));
        assertEquals(0, banco.commits());
    }

    // testar log da sessao aberta este teste confere o evento gravado sem gerar o historico completo
    @Test
    void gravaLogEmSessaoAbertaSemGerarHistorico() throws SQLException {
        // preparar sessao ainda aberta no contexto dos logs
        UUID sessao = UUID.randomUUID();
        MDC.put("sessaoLogId", sessao.toString());
        banco.quandoConsultar(SESSAO_LOG, linha("fim", null));

        // executar registro sem informar a sessao
        usuarios.InserirLogsNoBD(null, "INFO", "Classe", "operacao", null, "mensagem", null, null);

        // verificar sessao do contexto e historico nao gerado
        assertEquals(sessao, banco.ultimo("INSERT INTO public.\"LOGS_EVENTOS\"").valores().get(0));
        assertFalse(banco.executou("historico_gerado_em"));
        assertEquals(1, banco.commits());
    }

    // testar log da sessao encerrada este teste confere que o historico e refeito quando a sessao ja acabou
    @Test
    void refazHistoricoAoGravarLogEmSessaoEncerrada() throws SQLException {
        // preparar sessao encerrada

        // executar registro
        usuarios.InserirLogsNoBD(UUID.randomUUID(), "WARN", "Classe", "operacao", UUID.randomUUID(), "mensagem", "{}", "erro");

        // verificar historico atualizado
        assertTrue(banco.executou("historico_gerado_em"));
    }

    // testar falha ao gravar log este teste confere o desfazimento quando o evento nao e gravado
    @Test
    void desfazLogQuandoEventoNaoEhGravado() {
        // preparar evento que nao e inserido
        banco.quandoAlterar("INSERT INTO public.\"LOGS_EVENTOS\"", 0);

        // executar registro sem sessao
        SQLException erro = assertThrows(SQLException.class,
                () -> usuarios.InserirLogsNoBD(null, "INFO", "Classe", "operacao", null, "mensagem", null, null));

        // verificar mensagem e desfazimento
        assertTrue(erro.getMessage().contains("Nenhum log"));
        assertEquals(1, banco.rollbacks());
    }

    // testar fim da sessao este teste confere o registro do encerramento e a ligacao com o usuario
    @Test
    void encerraSessaoDeLogComUsuario() throws SQLException {
        // preparar sessao ainda aberta
        UUID sessao = UUID.randomUUID();
        Instant inicio = Instant.parse("2026-11-10T13:00:00Z");
        banco.quandoConsultar(SESSAO_LOG, linha("fim", null));

        // executar encerramento
        usuarios.finalizarSessaoLog(sessao, inicio, inicio.plusSeconds(600), " Ana@Teste.com ");

        // verificar evento de encerramento email normalizado e historico
        assertTrue(banco.ultimo("INSERT INTO public.\"LOGS_EVENTOS\"").valores().contains("sessionDestroyed"));
        List<Object> encerramento = banco.ultimo("status = 'ENCERRADA'").valores();
        assertEquals("ana@teste.com", encerramento.get(0));
        assertEquals(sessao, encerramento.get(3));
        assertTrue(banco.executou("historico_gerado_em"));
    }

    // testar fim repetido da sessao este teste confere que a sessao ja encerrada so tem o historico refeito
    @Test
    void naoRegistraEncerramentoDuasVezes() throws SQLException {
        // preparar sessao ja encerrada

        // executar encerramento sem email
        usuarios.finalizarSessaoLog(UUID.randomUUID(), Instant.now(), Instant.now(), null);

        // verificar que o encerramento nao foi gravado de novo
        assertFalse(banco.executou("status = 'ENCERRADA'"));
        assertTrue(banco.executou("historico_gerado_em"));
    }

    // testar sessao inexistente este teste confere o erro quando a sessao de log nao foi criada
    @Test
    void avisaSessaoDeLogInexistente() {
        // preparar sessao nao encontrada
        banco.semResultado(SESSAO_LOG);

        // executar encerramento
        SQLException erro = assertThrows(SQLException.class,
                () -> usuarios.finalizarSessaoLog(UUID.randomUUID(), Instant.now(), Instant.now(), "a@teste.com"));

        // verificar mensagem e desfazimento
        assertTrue(erro.getMessage().contains("Sessao de log"));
        assertEquals(1, banco.rollbacks());
    }

    // testar dados repetidos este teste confere os avisos de identificador email e telefone ja usados
    @Test
    void avisaDadosJaCadastrados() {
        // preparar banco com os tres dados ja usados

        // executar validacoes de aluno e professor
        List<String> aluno = usuarios.validarDadosDuplicados("Aluno", "123", " Ana@Teste.com ", "11999999999");
        banco.linhasPadrao(0);
        List<String> professor = usuarios.validarDadosDuplicados("Professor", "P1", "bia@teste.com", "11988888888");

        // verificar avisos e colunas consultadas
        assertEquals(List.of("Identificador já cadastrado", "Email já cadastrado", "Telefone já cadastrado"), aluno);
        assertTrue(professor.isEmpty());
        assertTrue(banco.executou("p.\"rgm\" = ?"));
        assertTrue(banco.executou("p.\"matricula\" = ?"));
        assertEquals(List.of("ana@teste.com"), banco.comandos().get(1).valores());
    }

    // testar falha na validacao este teste confere que erro do banco interrompe o cadastro
    @Test
    void interrompeValidacaoQuandoBancoFalha() {
        // preparar falha na consulta
        banco.falharAo("SELECT 1 FROM");

        // executar validacao
        RuntimeException erro = assertThrows(RuntimeException.class,
                () -> usuarios.validarDadosDuplicados("Aluno", "123", "a@teste.com", "1"));

        // verificar causa original
        assertTrue(erro.getCause() instanceof SQLException);
    }

    // testar senha para login este teste confere a consulta da senha pelo perfil de cada tela de login
    @Test
    void buscaSenhaPeloPerfilDoLogin() throws SQLException {
        // preparar senha guardada
        banco.quandoConsultar("SELECT u.\"senha\"", linha("senha", "hash"));

        // executar consultas por perfil
        String aluno = usuarios.QueryLoginUsuario("Aluno", " A@Teste.com ");
        String professor = usuarios.QueryLoginUsuario("Professor", "p@teste.com");
        String admin = usuarios.QueryLoginUsuario("Admin", "adm@teste.com");
        banco.semResultado("SELECT u.\"senha\"");
        String semConta = usuarios.QueryLoginUsuario("Administrador", "nada@teste.com");

        // verificar senhas tabelas e email normalizado
        assertEquals("hash", aluno);
        assertEquals("hash", professor);
        assertEquals("hash", admin);
        assertEquals("", semConta);
        assertEquals(List.of("a@teste.com"), banco.comandos().get(0).valores());
        assertTrue(banco.comandos().get(1).sql().contains("PROFESSORES"));
        assertTrue(banco.comandos().get(2).sql().contains("ADMINISTRADOR"));
    }

    // testar contas para login este teste confere a busca de aluno professor administrador e perfil pelo email
    @Test
    void buscaContasAtivasPeloEmail() throws SQLException {
        // preparar conta encontrada
        banco.quandoConsultar("u.\"email\", u.\"senha\"", linha("email", "a@teste.com", "senha", "hash"));
        banco.quandoConsultar("SELECT u.\"perfil\"", linha("perfil", "PROFESSORES"));

        // executar buscas com conta
        String aluno = usuarios.buscarPorEmailAluno("a@teste.com").getSenha();
        String professor = usuarios.buscarPorEmailProfessor("a@teste.com").getEmail();
        String admin = usuarios.buscarPorEmailAdmin("a@teste.com").getSenha();
        String perfil = usuarios.buscarPorTipoUsuario("a@teste.com");

        // verificar dados encontrados
        assertEquals("hash", aluno);
        assertEquals("a@teste.com", professor);
        assertEquals("hash", admin);
        assertEquals("PROFESSORES", perfil);
    }

    // testar contas inexistentes este teste confere o retorno nulo das buscas sem conta ativa
    @Test
    void devolveNuloParaContaInexistente() throws SQLException {
        // preparar banco sem contas
        banco.linhasPadrao(0);

        // executar buscas
        boolean todasNulas = usuarios.buscarPorEmailAluno("x@teste.com") == null
                && usuarios.buscarPorEmailProfessor("x@teste.com") == null
                && usuarios.buscarPorEmailAdmin("x@teste.com") == null
                && usuarios.buscarPorTipoUsuario("x@teste.com") == null;

        // verificar retornos nulos
        assertTrue(todasNulas);
    }

    // testar troca de senha este teste confere a nova senha gravada para o email
    @Test
    void atualizaSenhaDoUsuario() throws SQLException {
        // preparar nova senha criptografada

        // executar troca
        usuarios.UpdateSenhaUsuario("novoHash", "a@teste.com");

        // verificar comando enviado
        assertEquals(List.of("novoHash", "a@teste.com"), banco.ultimo("SET \"senha\" = ?").valores());
    }
}
