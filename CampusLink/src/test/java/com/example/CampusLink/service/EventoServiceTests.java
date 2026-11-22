// verificacao dos eventos confere horario valido cadastro com materiais edicao validada e exclusao
package com.example.CampusLink.service;

import com.example.CampusLink.model.Evento;
import com.example.CampusLink.repository.EventoConteudoRepository;
import com.example.CampusLink.repository.EventoRepository;
import com.example.CampusLink.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// testes dos eventos esta classe simula o banco de eventos e dos materiais associados
class EventoServiceTests {

    private static final LocalDateTime INICIO = LocalDateTime.of(2026, 11, 12, 19, 0);

    private EventoRepository eventos;
    private EventoConteudoRepository materiais;
    private EventoService servico;

    // preparar testes dos eventos este metodo cria o servico com o banco simulado e nomes de turma e materiais
    @BeforeEach
    void preparar() {
        eventos = mock(EventoRepository.class);
        materiais = mock(EventoConteudoRepository.class);
        servico = new EventoService(eventos, materiais);
        ReflectionTestUtils.setField(servico, "UsuarioRepository", mock(UsuarioRepository.class));
        when(eventos.buscarNomeTurma(1L)).thenReturn(Optional.of("Calculo"));
        when(eventos.buscarConteudosDoEvento(5L)).thenReturn(List.of("Apostila", "Lista"));
        // listagem completa turmas e materiais de todos os eventos em uma consulta de cada
        when(eventos.buscarNomesTurmas(any())).thenReturn(List.<Object[]>of(new Object[]{1L, "Calculo"}));
        when(eventos.buscarConteudosDosEventos(any())).thenReturn(List.of(new Object[]{5L, "Apostila"}, new Object[]{5L, "Lista"}));
        when(eventos.save(any())).thenAnswer(chamada -> chamada.getArgument(0));
        when(eventos.saveAndFlush(any())).thenAnswer(chamada -> chamada.getArgument(0));
    }

    // testar horario do evento este teste confere termino antes do inicio evento em dois dias e horario valido
    @Test
    void validaHorarioDoEvento() {
        // preparar horarios

        // executar validacoes
        String terminaAntes = EventoService.validarHorario(INICIO, INICIO.minusMinutes(30));
        String mesmoHorario = EventoService.validarHorario(INICIO, INICIO);
        String doisDias = EventoService.validarHorario(INICIO, INICIO.plusHours(6));
        String valido = EventoService.validarHorario(INICIO, INICIO.plusHours(2));

        // verificar mensagens
        assertTrue(terminaAntes.contains("depois do início"));
        assertTrue(mesmoHorario.contains("depois do início"));
        assertTrue(doisDias.contains("mesmo dia"));
        assertNull(valido);
    }

    // testar cadastro com materiais este teste confere que os materiais escolhidos sao ligados ao evento salvo
    @Test
    void cadastraEventoComMateriais() throws SQLException {
        // preparar evento novo
        Evento evento = evento(5L);

        // executar cadastro com materiais e sem materiais
        Evento salvo = servico.adicionarEvento(evento, List.of(10L, 11L));
        servico.adicionarEvento(evento(6L), List.of());

        // verificar materiais associados e detalhes preenchidos
        verify(materiais).associarConteudos(5L, List.of(10L, 11L));
        verify(materiais, never()).associarConteudos(org.mockito.ArgumentMatchers.eq(6L), any());
        assertEquals("Calculo", salvo.getNomeTurma());
        assertEquals(List.of("Apostila", "Lista"), salvo.getNomesConteudos());
    }

    // testar falha no cadastro este teste confere a mensagem quando os materiais nao podem ser ligados
    @Test
    void informaFalhaAoLigarMateriais() throws SQLException {
        // preparar falha ao associar materiais
        doThrow(new SQLException("banco")).when(materiais).associarConteudos(anyLong(), any());

        // executar cadastro
        RuntimeException erro = assertThrows(RuntimeException.class, () -> servico.adicionarEvento(evento(5L), List.of(10L)));

        // verificar mensagem
        assertTrue(erro.getMessage().contains("associar conteudos"));
    }

    // testar consultas este teste confere a listagem a busca por codigo o evento inexistente e o codigo nulo
    @Test
    void consultaEventosComDetalhes() {
        // preparar evento sem turma e evento da turma 1
        Evento semTurma = evento(7L);
        semTurma.setIdTurma(null);
        when(eventos.findAll()).thenReturn(List.of(evento(5L), semTurma));
        when(eventos.findById(5L)).thenReturn(Optional.of(evento(5L)));

        // executar consultas
        List<Evento> lista = servico.listarEventos();
        Evento encontrado = servico.buscarEventoPorId(5L);
        Evento inexistente = servico.buscarEventoPorId(8L);
        Evento semCodigo = servico.buscarEventoPorId(null);

        // verificar detalhes e retornos nulos
        assertEquals("Calculo", lista.get(0).getNomeTurma());
        assertNull(lista.get(1).getNomeTurma());
        assertEquals(2, encontrado.getNomesConteudos().size());
        assertNull(inexistente);
        assertNull(semCodigo);
    }

    // testar lista do administrador este teste confere o filtro enviado ao banco e a volta para a ultima pagina
    @Test
    void paginaEventosDoAdministradorNoBanco() {
        // preparar banco com 30 eventos encontrados: a pagina 9 vem vazia e a pagina 1 traz o evento
        when(eventos.buscarParaAdmin(eq("%50\\%%"), eq("PROVA"), eq(""), eq(PageRequest.of(9, 25))))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(9, 25), 30));
        when(eventos.buscarParaAdmin(eq("%50\\%%"), eq("PROVA"), eq(""), eq(PageRequest.of(1, 25))))
                .thenReturn(new PageImpl<>(List.of(evento(5L)), PageRequest.of(1, 25), 30));

        // executar busca pedindo uma pagina alem da ultima
        Page<Evento> pagina = servico.listarEventosParaAdmin(" 50% ", "PROVA", "", 9, 25);

        // verificar pagina corrigida e detalhes preenchidos so nos eventos da pagina
        assertEquals(1, pagina.getNumber());
        assertEquals("Calculo", pagina.getContent().get(0).getNomeTurma());
    }

    // testar falha na listagem este teste confere que o erro do banco chega ao controlador
    @Test
    void repassaFalhaAoListarEventos() {
        // preparar falha do banco
        DataRetrievalFailureException falha = new DataRetrievalFailureException("banco");
        when(eventos.findAll()).thenThrow(falha);

        // executar listagem
        DataRetrievalFailureException erro = assertThrows(DataRetrievalFailureException.class, () -> servico.listarEventos());

        // verificar erro original
        assertSame(falha, erro);
    }

    // testar edicao do evento este teste confere os dados copiados nome sem espacos e troca dos materiais
    @Test
    void editaEventoETrocaMateriais() {
        // preparar evento existente e dados novos
        when(eventos.findById(5L)).thenReturn(Optional.of(evento(5L)));
        Evento dados = evento(null);
        dados.setNome("  Prova final  ");
        dados.setPrioridade("alta");

        // executar edicao
        Evento editado = servico.atualizarEvento(5L, dados, List.of(12L));

        // verificar dados e troca dos materiais
        assertEquals("Prova final", editado.getNome());
        assertEquals("ALTA", editado.getPrioridade());
        verify(materiais).substituirConteudosDoEvento(5L, List.of(12L));
    }

    // testar edicao invalida este teste confere dados ausentes campos obrigatorios periodo invertido e evento inexistente
    @Test
    void recusaEdicaoInvalida() {
        // preparar dados invalidos
        Evento semNome = evento(null);
        semNome.setNome(" ");
        Evento semProfessor = evento(null);
        semProfessor.setIdProfessor(null);
        Evento invertido = evento(null);
        invertido.setFim(INICIO.minusHours(1));

        // executar edicoes e verificar mensagens
        assertEquals("Informe os dados do evento", recusa(() -> servico.atualizarEvento(null, evento(null), null)));
        assertEquals("Informe os dados do evento", recusa(() -> servico.atualizarEvento(5L, null, null)));
        assertEquals("Preencha os campos obrigatorios do evento", recusa(() -> servico.atualizarEvento(5L, semNome, null)));
        assertEquals("Preencha os campos obrigatorios do evento", recusa(() -> servico.atualizarEvento(5L, semProfessor, null)));
        assertEquals("A data final nao pode ser anterior ao inicio", recusa(() -> servico.atualizarEvento(5L, invertido, null)));
        assertEquals("Evento nao encontrado", recusa(() -> servico.atualizarEvento(99L, evento(null), null)));
        verify(eventos, never()).saveAndFlush(any());
    }

    // testar exclusao este teste confere que os materiais saem antes do evento e os erros de exclusao
    @Test
    void excluiEventoComSeusMateriais() throws SQLException {
        // preparar evento existente e falha no evento 6
        when(eventos.existsById(5L)).thenReturn(true);
        when(eventos.existsById(6L)).thenReturn(true);
        doThrow(new SQLException("banco")).when(materiais).excluirAssociacoesDoEvento(6L);

        // executar exclusoes
        servico.excluirEvento(5L);
        String semCodigo = recusa(() -> servico.excluirEvento(null));
        String inexistente = recusa(() -> servico.excluirEvento(99L));
        RuntimeException falha = assertThrows(RuntimeException.class, () -> servico.excluirEvento(6L));

        // verificar exclusao e mensagens
        verify(materiais).excluirAssociacoesDoEvento(5L);
        verify(eventos).deleteById(5L);
        assertTrue(semCodigo.contains("nao pode ser nulo"));
        assertEquals("Evento nao encontrado", inexistente);
        assertEquals("Erro ao excluir evento", falha.getMessage());
    }

    // obter mensagem de recusa este metodo executa a acao e devolve o motivo do erro de validacao
    private String recusa(org.junit.jupiter.api.function.Executable acao) {
        return assertThrows(IllegalArgumentException.class, acao).getMessage();
    }

    // montar evento do teste este metodo cria um evento de duas horas da turma 1 e do professor 4
    private Evento evento(Long id) {
        Evento evento = new Evento("Prova", INICIO, INICIO.plusHours(2), null);
        ReflectionTestUtils.setField(evento, "id", id);
        evento.setIdTurma(1L);
        evento.setIdProfessor(4L);
        return evento;
    }
}
