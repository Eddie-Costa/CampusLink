// verificacao do banco de chamados confere usuario ativo por perfil acesso ao proprio chamado e mudancas de situacao
package com.example.CampusLink.repository;

import com.example.CampusLink.dto.ChamadoDTO;
import com.example.CampusLink.dto.ChamadoRespostaDTO;
import com.example.CampusLink.dto.DadosUsuarioDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static com.example.CampusLink.repository.BancoSimulado.linha;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// testes do banco de chamados esta classe usa o banco simulado com jdbctemplate real
class ChamadoRepositoryTests {

    private BancoSimulado banco;
    private ChamadoRepository chamados;

    // preparar testes dos chamados este metodo liga o repositorio ao banco simulado
    @BeforeEach
    void preparar() {
        banco = new BancoSimulado();
        chamados = new ChamadoRepository(banco.jdbcTemplate());
    }

    // testar usuario do chamado este teste confere a tabela do perfil e o perfil desconhecido
    @Test
    void buscaUsuarioAtivoPeloPerfil() {
        // preparar usuario ativo
        banco.quandoConsultar("SELECT u.id, u.nome, u.email", linha("id", 2L, "nome", "Ana", "email", "ana@teste.com"));

        // executar buscas por perfil
        DadosUsuarioDTO aluno = chamados.buscarUsuarioAtivo(" Ana@Teste.com ", "aluno");
        DadosUsuarioDTO professor = chamados.buscarUsuarioAtivo("p@teste.com", "professor");
        DadosUsuarioDTO admin = chamados.buscarUsuarioAtivo("a@teste.com", "admin");
        DadosUsuarioDTO outro = chamados.buscarUsuarioAtivo("x@teste.com", "visitante");
        banco.semResultado("SELECT u.id, u.nome, u.email");
        DadosUsuarioDTO inexistente = chamados.buscarUsuarioAtivo("nada@teste.com", "aluno");

        // verificar perfis tabelas e email normalizado
        assertEquals("ALUNOS", aluno.getPerfil());
        assertEquals("PROFESSORES", professor.getPerfil());
        assertEquals("ADMINISTRADOR", admin.getPerfil());
        assertNull(outro);
        assertNull(inexistente);
        assertEquals(List.of("ana@teste.com"), banco.comandos().get(0).valores());
    }

    // testar abertura e listagens este teste confere o codigo do chamado e os filtros do usuario e do administrador
    @Test
    void abreEListaChamados() {
        // preparar codigo do chamado e chamado sem conclusao
        banco.quandoConsultar("RETURNING id", linha("id", 15L));
        banco.quandoConsultar("SELECT COUNT(*) FROM public.\"CHAMADOS\"", linha("count", 1L));
        banco.quandoConsultar("ORDER BY c.created_at DESC", linha("id", 15L, "status", "aberto", "concluido_em", null,
                "id_admin_conclusao", null, "created_at", LocalDateTime.of(2026, 11, 10, 13, 0)));

        // executar abertura e listagens
        Long id = chamados.inserir(2L, "Erro", "Nao abre");
        List<ChamadoDTO> doUsuario = chamados.listarDoUsuario(2L);
        chamados.listarParaAdmin("", 0);
        var abertos = chamados.listarParaAdmin("aberto", 3);

        // verificar codigo dados e filtros
        assertEquals(15L, id);
        assertNull(doUsuario.get(0).getConcluidoEm());
        assertEquals("aberto", doUsuario.get(0).getStatus());
        assertEquals(List.of(2L), banco.comandos().get(1).valores());
        // cada listagem conta o total filtrado e busca so a pagina, ajustada para a ultima existente
        assertTrue(banco.comandos().get(2).valores().isEmpty());
        assertEquals(List.of(0), banco.comandos().get(3).valores());
        assertEquals(List.of("aberto"), banco.comandos().get(4).valores());
        assertEquals(List.of("aberto", 0), banco.comandos().get(5).valores());
        assertEquals(0, abertos.getNumber());
    }

    // testar chamado de outro usuario este teste confere que a busca exige o dono e permite bloquear a linha
    @Test
    void buscaChamadoSoDoDonoEComBloqueio() {
        // preparar chamado inexistente para o usuario 3
        banco.semResultado("AND c.id_usuario = ?");

        // executar buscas
        ChamadoDTO alheio = chamados.buscarDoUsuario(15L, 3L);
        chamados.buscarDoUsuario(15L, 2L, true);
        ChamadoDTO porId = chamados.buscarPorId(15L, true);
        chamados.buscarPorId(15L, false);

        // verificar nulo filtro pelo dono e bloqueio apenas quando pedido
        assertNull(alheio);
        assertEquals(List.of(15L, 3L), banco.comandos().get(0).valores());
        assertTrue(banco.comandos().get(1).sql().endsWith("FOR UPDATE OF c"));
        assertEquals(1L, porId.getId());
        assertFalse(banco.comandos().get(3).sql().contains("FOR UPDATE"));
    }

    // testar respostas e situacao este teste confere as respostas e a situacao apos cada mensagem
    @Test
    void registraRespostasEAtualizaSituacao() {
        // preparar resposta de usuario sem data
        banco.quandoConsultar("CHAMADO_RESPOSTAS", linha("id", 1L, "id_admin_usuario", null, "id_usuario", 2L,
                "tipo_autor", "usuario", "nome_autor", "Usuario removido", "created_at", null));

        // executar consultas e mensagens
        List<ChamadoRespostaDTO> respostas = chamados.listarRespostas(15L);
        chamados.inserirResposta(15L, 1L, "Resolvido");
        chamados.inserirMensagemUsuario(15L, 2L, "Ainda nao");
        chamados.concluir(15L, 1L);

        // verificar autor e situacoes gravadas
        assertNull(respostas.get(0).getIdAdminUsuario());
        assertNull(respostas.get(0).getCreatedAt());
        assertTrue(banco.comandos().get(2).sql().contains("status = 'respondido'"));
        assertTrue(banco.comandos().get(4).sql().contains("status = 'aberto'"));
        assertEquals(List.of(1L, 15L), banco.ultimo("status = 'concluido'").valores());
    }
}
