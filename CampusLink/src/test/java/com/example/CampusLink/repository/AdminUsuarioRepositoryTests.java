// verificacao da gestao de usuarios confere edicao duplicidade desativacao e exclusao com as regras dos administradores
package com.example.CampusLink.repository;

import com.example.CampusLink.dto.Admin.usuarioAdminDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Date;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static com.example.CampusLink.repository.BancoSimulado.linha;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// testes da gestao de usuarios esta classe simula administradores master comuns alunos e professores
class AdminUsuarioRepositoryTests {

    private static final String ADMIN_POR_EMAIL = "WHERE LOWER(u.email) = ? AND adm.id_usuario IS NOT NULL";
    private static final String USUARIO_POR_ID = "WHERE u.id = ? AND (";
    private static final String ADMINS_ATIVOS = "SELECT COUNT(*)";

    private BancoSimulado banco;
    private AdminUsuarioRepository usuarios;

    // preparar testes da gestao este metodo liga o repositorio ao banco simulado e a um administrador comum logado
    @BeforeEach
    void preparar() {
        banco = new BancoSimulado();
        usuarios = new AdminUsuarioRepository(banco.jdbcTemplate(), banco.transacoes());
        banco.quandoConsultar(ADMIN_POR_EMAIL, admin(1L, true, false));
        banco.quandoConsultar(ADMINS_ATIVOS, linha("count", 3));
    }

    // testar filtros da lista este teste confere busca por texto e email no banco e a pagina limitada ao total
    @Test
    void filtraEPaginaUsuariosNoBanco() throws SQLException {
        // preparar 45 contas encontradas, o que da 3 paginas de 20
        banco.quandoConsultar("SELECT COUNT(*) FROM public.\"USUARIOS\" u LEFT JOIN", linha("count", 45L));

        // executar busca pedindo uma pagina alem da ultima
        var pagina = usuarios.listarUsuarios(" ana ", " Ana@Teste.com ", 9);

        // verificar filtros enviados e pagina corrigida para a ultima
        assertEquals(2, pagina.getNumber());
        assertEquals(3, pagina.getTotalPages());
        assertEquals(List.of("%ana%", "%ana%", "%ana%", "ana", "ana@teste.com", 40),
                banco.ultimo("ORDER BY u.nome, u.id LIMIT").valores());
    }

    // testar listagem este teste confere o perfil identificado para administrador aluno e professor
    @Test
    void identificaPerfilDeCadaUsuario() throws SQLException {
        // preparar tres usuarios de perfis diferentes
        banco.quandoConsultar("ORDER BY u.nome, u.id",
                admin(1L, true, true),
                linha("id_usuario", 2L, "id_aluno", 7L, "id_professor", null, "id_admin", null, "rgm", "123", "datanasc", LocalDate.of(2004, 3, 1)),
                linha("id_usuario", 3L, "id_aluno", null, "id_professor", 4L, "id_admin", null, "matricula", "P1", "datanasc", null));

        // executar listagem
        List<usuarioAdminDTO> lista = usuarios.listarUsuarios(null, null, 0).getContent();

        // verificar perfis e identificadores
        assertEquals("Administrador", lista.get(0).getPerfil());
        assertEquals("1", lista.get(0).getIdentificador());
        assertTrue(lista.get(0).isMaster());
        assertEquals("Aluno", lista.get(1).getPerfil());
        assertEquals("123", lista.get(1).getIdentificador());
        assertEquals("2004-03-01", lista.get(1).getDataNasc());
        assertEquals("Professor", lista.get(2).getPerfil());
        assertEquals(4L, lista.get(2).getIdPerfil());
        assertNull(lista.get(2).getDataNasc());
    }

    // testar buscas individuais este teste confere usuario por codigo e administrador por email
    @Test
    void buscaUsuarioEAdministrador() throws SQLException {
        // preparar usuario inexistente
        banco.semResultado(USUARIO_POR_ID);

        // executar buscas
        usuarioAdminDTO inexistente = usuarios.buscarUsuarioPorId(99L);
        usuarioAdminDTO admin = usuarios.buscarAdministradorPorEmail(" Admin@Teste.com ");
        usuarioAdminDTO semEmail = usuarios.buscarAdministradorPorEmail(" ");

        // verificar retornos e email normalizado
        assertNull(inexistente);
        assertEquals("Administrador", admin.getPerfil());
        assertEquals(List.of("admin@teste.com"), banco.ultimo(ADMIN_POR_EMAIL).valores());
        assertNull(semEmail);
    }

    // testar dados repetidos este teste confere os avisos de email rgm e matricula usados por outra conta
    @Test
    void avisaDadosUsadosPorOutraConta() throws SQLException {
        // preparar banco com todos os dados ja usados

        // executar validacoes de aluno e professor
        List<String> aluno = usuarios.validarDadosEdicao(2L, "Aluno", "123", " A@Teste.com ");
        List<String> professor = usuarios.validarDadosEdicao(3L, "Professor", "P1", "p@teste.com");

        // verificar avisos e conta ignorada na busca
        assertEquals(List.of("E-mail já cadastrado para outro usuário.",
                "RGM já cadastrado para outro aluno."), aluno);
        assertEquals("Matrícula já cadastrada para outro professor.", professor.get(1));
        assertEquals(List.of("a@teste.com", 2L), banco.comandos().get(0).valores());
    }

    // testar dados livres este teste confere que nenhum aviso aparece quando os dados nao sao usados
    @Test
    void aceitaDadosLivres() throws SQLException {
        // preparar banco sem dados repetidos
        banco.linhasPadrao(0);

        // executar validacao
        List<String> erros = usuarios.validarDadosEdicao(2L, "Aluno", "123", "a@teste.com");

        // verificar lista vazia
        assertTrue(erros.isEmpty());
    }

    // testar edicao do aluno este teste confere rgm e dados pessoais gravados na mesma transacao
    @Test
    void editaAlunoComRgmEDadosPessoais() throws SQLException {
        // preparar aluno editado
        usuarioAdminDTO aluno = editado("Aluno", "999");

        // executar edicao
        boolean editou = usuarios.atualizarUsuario(aluno);

        // verificar rgm e dados enviados
        assertTrue(editou);
        assertEquals(List.of("999", 2L), banco.ultimo("SET rgm = ?").valores());
        assertEquals(List.of("Ana", "ana@teste.com", Date.valueOf("2004-03-01"), 2L),
                banco.ultimo("SET nome = ?").valores());
    }

    // testar edicao sem efeito este teste confere perfil invalido e contas nao encontradas
    @Test
    void recusaEdicaoSemPerfilOuSemConta() throws SQLException {
        // preparar professor sem cadastro na tabela de professores
        banco.quandoAlterar("SET matricula = ?", 0);

        // executar edicoes invalidas
        boolean administrador = usuarios.atualizarUsuario(editado("Administrador", "1"));
        boolean semPerfil = usuarios.atualizarUsuario(editado("Professor", "P9"));
        banco.quandoAlterar("SET matricula = ?", 1).quandoAlterar("SET nome = ?", 0);
        boolean semUsuario = usuarios.atualizarUsuario(editado("Professor", "P9"));

        // verificar recusas e desfazimento
        assertFalse(administrador);
        assertFalse(semPerfil);
        assertFalse(semUsuario);
        assertEquals(2, banco.rollbacks());
    }

    // testar situacao simples este teste confere que a troca de situacao simples ignora administradores
    @Test
    void trocaSituacaoSemAtingirAdministradores() throws SQLException {
        // preparar conta que nao pode ser alterada
        banco.quandoAlterar("AND NOT EXISTS", 0);

        // executar troca
        boolean alterou = usuarios.atualizarStatusUsuario(2L, false);

        // verificar recusa e parametros
        assertFalse(alterou);
        assertEquals(List.of(false, 2L), banco.ultimo("AND NOT EXISTS").valores());
    }

    // testar desativacao de aluno este teste confere a desativacao feita por administrador ativo
    @Test
    void desativaAlunoComAdministradorAtivo() throws SQLException {
        // preparar aluno ativo
        banco.quandoConsultar(USUARIO_POR_ID, aluno(2L));

        // executar desativacao
        boolean desativou = usuarios.atualizarStatusUsuario(2L, false, "admin@teste.com");

        // verificar bloqueio da tabela e troca da situacao
        assertTrue(desativou);
        assertTrue(banco.executou("LOCK TABLE"));
        assertEquals(List.of(false, 2L), banco.ultimo("SET status = ? WHERE id = ?").valores());
    }

    // testar administrador indisponivel este teste confere que administrador inativo ou sem sessao nao altera contas
    @Test
    void recusaAlteracaoDeAdministradorIndisponivel() {
        // preparar administrador logado inativo
        banco.quandoConsultar(ADMIN_POR_EMAIL, admin(1L, false, false));

        // executar alteracoes
        IllegalStateException inativo = assertThrows(IllegalStateException.class, () -> usuarios.atualizarStatusUsuario(2L, false, "admin@teste.com"));
        IllegalStateException semEmail = assertThrows(IllegalStateException.class, () -> usuarios.excluirAdministrador(5L, null));

        // verificar mensagens
        assertTrue(inativo.getMessage().contains("não está disponível"));
        assertTrue(semEmail.getMessage().contains("não está disponível"));
    }

    // testar conta inexistente este teste confere que nada e alterado para usuario que nao existe
    @Test
    void ignoraAlteracaoDeContaInexistente() throws SQLException {
        // preparar usuario inexistente
        banco.semResultado(USUARIO_POR_ID);

        // executar alteracoes
        boolean situacao = usuarios.atualizarStatusUsuario(99L, false, "admin@teste.com");
        boolean exclusao = usuarios.excluirAdministrador(99L, "admin@teste.com");

        // verificar recusas
        assertFalse(situacao);
        assertFalse(exclusao);
        assertFalse(banco.executou("SET status = ? WHERE id = ?"));
    }

    // testar conta master este teste confere que so outro master altera um administrador master
    @Test
    void somenteMasterAlteraOutroMaster() {
        // preparar administrador master como alvo
        banco.quandoConsultar(USUARIO_POR_ID, admin(5L, true, true));

        // executar desativacao por administrador comum
        IllegalStateException erro = assertThrows(IllegalStateException.class, () -> usuarios.atualizarStatusUsuario(5L, false, "admin@teste.com"));

        // verificar mensagem
        assertTrue(erro.getMessage().contains("outro administrador master"));
    }

    // testar ultimo administrador este teste confere que o sistema sempre mantem um administrador ativo
    @Test
    void mantemPeloMenosUmAdministradorAtivo() {
        // preparar alvo como unico administrador ativo
        banco.quandoConsultar(USUARIO_POR_ID, admin(5L, true, false));
        banco.quandoConsultar(ADMINS_ATIVOS, linha("count", 1));

        // executar desativacao
        IllegalStateException erro = assertThrows(IllegalStateException.class, () -> usuarios.atualizarStatusUsuario(5L, false, "admin@teste.com"));

        // verificar mensagem
        assertTrue(erro.getMessage().contains("pelo menos um administrador ativo"));
    }

    // testar propria conta este teste confere que o administrador nao desativa nem exclui a si mesmo
    @Test
    void administradorNaoRetiraAPropriaConta() {
        // preparar alvo igual ao administrador logado
        banco.quandoConsultar(USUARIO_POR_ID, admin(1L, true, false));

        // executar desativacao e exclusao da propria conta
        IllegalStateException desativar = assertThrows(IllegalStateException.class, () -> usuarios.atualizarStatusUsuario(1L, false, "admin@teste.com"));
        IllegalStateException excluir = assertThrows(IllegalStateException.class, () -> usuarios.excluirAdministrador(1L, "admin@teste.com"));

        // verificar mensagens
        assertTrue(desativar.getMessage().contains("sua própria conta"));
        assertTrue(excluir.getMessage().contains("sua própria conta"));
    }

    // testar reativacao de administrador este teste confere que reativar outro administrador nao exige as regras de retirada
    @Test
    void reativaOutroAdministrador() throws SQLException {
        // preparar administrador inativo
        banco.quandoConsultar(USUARIO_POR_ID, admin(5L, false, false));

        // executar reativacao
        boolean reativou = usuarios.atualizarStatusUsuario(5L, true, "admin@teste.com");

        // verificar troca da situacao
        assertTrue(reativou);
        assertEquals(List.of(true, 5L), banco.ultimo("SET status = ? WHERE id = ?").valores());
    }

    // testar exclusao de administrador este teste confere a remocao do cargo e da conta
    @Test
    void excluiOutroAdministrador() throws SQLException {
        // preparar outro administrador
        banco.quandoConsultar(USUARIO_POR_ID, admin(5L, true, false));

        // executar exclusao
        boolean excluiu = usuarios.excluirAdministrador(5L, "admin@teste.com");

        // verificar remocao do cargo e da conta
        assertTrue(excluiu);
        assertEquals(List.of(5L), banco.ultimo("DELETE FROM public.\"ADMINISTRADOR\"").valores());
        assertTrue(banco.executou("DELETE FROM public.\"USUARIOS\" u"));
    }

    // testar exclusao invalida este teste confere que aluno nao e excluido como administrador e conta com outro perfil exige desativacao
    @Test
    void recusaExclusaoQueApagariaOutroPerfil() throws SQLException {
        // preparar aluno como alvo
        banco.quandoConsultar(USUARIO_POR_ID, aluno(2L));

        // executar exclusao de aluno e de administrador que tambem e professor
        boolean aluno = usuarios.excluirAdministrador(2L, "admin@teste.com");
        banco.quandoConsultar(USUARIO_POR_ID, admin(5L, true, false)).quandoAlterar("DELETE FROM public.\"USUARIOS\" u", 0);
        IllegalStateException comOutroPerfil = assertThrows(IllegalStateException.class, () -> usuarios.excluirAdministrador(5L, "admin@teste.com"));

        // verificar recusa e mensagem
        assertFalse(aluno);
        assertTrue(comOutroPerfil.getMessage().contains("Utilize a desativação"));
    }

    // montar administrador este metodo cria a linha de um administrador com situacao e cargo master
    private Map<String, Object> admin(Long id, boolean ativo, boolean master) {
        return linha("id_usuario", id, "id_aluno", null, "id_professor", null, "id_admin", id,
                "status", ativo, "master", master, "datanasc", null);
    }

    // montar aluno este metodo cria a linha de um aluno ativo
    private Map<String, Object> aluno(Long id) {
        return linha("id_usuario", id, "id_aluno", 7L, "id_professor", null, "id_admin", null,
                "status", true, "master", false, "rgm", "123", "datanasc", null);
    }

    // montar usuario editado este metodo cria os dados enviados pela tela de edicao
    private usuarioAdminDTO editado(String perfil, String identificador) {
        usuarioAdminDTO usuario = new usuarioAdminDTO();
        usuario.setIdUsuario(2L);
        usuario.setPerfil(perfil);
        usuario.setIdentificador(identificador);
        usuario.setNome("Ana");
        usuario.setEmail(" Ana@Teste.com ");
        usuario.setDataNasc("2004-03-01");
        return usuario;
    }
}
