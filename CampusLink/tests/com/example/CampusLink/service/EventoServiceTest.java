package com.example.CampusLink.service;

import com.example.CampusLink.dao.EventoConteudoDAO;
import com.example.CampusLink.dao.EventoDetalhesDAO;
import com.example.CampusLink.dao.usuarioDAO;
import com.example.CampusLink.model.Evento;
import com.example.CampusLink.repository.EventoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventoServiceTest {

    @Mock
    private EventoRepository eventoRepository;

    @Mock
    private EventoConteudoDAO eventoConteudoDAO;

    @Mock
    private EventoDetalhesDAO eventoDetalhesDAO;

    @Mock
    private usuarioDAO usuarios;

    private EventoService eventoService;

    @BeforeEach
    void preparar() {
        eventoService = new EventoService(eventoRepository, eventoConteudoDAO, eventoDetalhesDAO);
        // Isola também a gravação de logs no banco, sem iniciar o Spring.
        ReflectionTestUtils.setField(eventoService, "usuarioDAO", usuarios);
    }

    @Test
    void deveSalvarEventoAssociarConteudosERetornarDetalhes() throws Exception {
        // Arrange: prepara o evento recebido e uma instância retornada pela persistência.
        LocalDateTime inicio = LocalDateTime.of(2026, 10, 20, 14, 0);
        LocalDateTime fim = inicio.plusHours(2);
        Evento evento = new Evento("Revisão de testes", inicio, fim, null, "ALTA");
        evento.setIdTurma(20L);
        evento.setIdProfessor(30L);

        Evento eventoSalvo = new Evento("Revisão de testes", inicio, fim, null, "ALTA");
        eventoSalvo.setIdTurma(20L);
        eventoSalvo.setIdProfessor(30L);
        // O ID normalmente é gerado pelo banco e não possui setter na entidade.
        ReflectionTestUtils.setField(eventoSalvo, "id", 10L);

        List<Long> idsConteudos = List.of(40L, 50L);
        List<String> nomesConteudos = List.of("Integração", "Testes unitários");
        when(eventoRepository.save(evento)).thenReturn(eventoSalvo);
        when(eventoDetalhesDAO.buscarNomeTurma(20L)).thenReturn("Engenharia de Software");
        when(eventoDetalhesDAO.buscarConteudosDoEvento(10L)).thenReturn(nomesConteudos);

        // Act: executa o método real do serviço com as dependências simuladas.
        Evento resultado = eventoService.adicionarEvento(evento, idsConteudos);

        // Assert: retorna o evento salvo com os detalhes e associa usando o ID gerado.
        assertAll(
                () -> assertSame(eventoSalvo, resultado),
                () -> assertEquals("Engenharia de Software", resultado.getNomeTurma()),
                () -> assertEquals(nomesConteudos, resultado.getNomesConteudos())
        );

        InOrder ordem = inOrder(eventoRepository, eventoConteudoDAO);
        ordem.verify(eventoRepository).save(evento);
        ordem.verify(eventoConteudoDAO).associarConteudos(10L, idsConteudos);
    }

    @Test
    void deveImpedirExclusaoQuandoEventoNaoExiste() throws Exception {
        // Arrange: simula um ID que não está cadastrado no repositório.
        Long idEvento = 99L;
        when(eventoRepository.existsById(idEvento)).thenReturn(false);

        // Act: tenta excluir e captura a exceção, verificando seu tipo.
        IllegalArgumentException erro = assertThrows(
                IllegalArgumentException.class,
                () -> eventoService.excluirEvento(idEvento)
        );

        // Assert: confere a mensagem e garante que nenhuma exclusão foi solicitada.
        assertEquals("Evento nao encontrado", erro.getMessage());
        verify(eventoRepository).existsById(idEvento);
        verify(eventoRepository, never()).deleteById(any());
        verify(eventoConteudoDAO, never()).excluirAssociacoesDoEvento(any());
    }

    @Test
    void deveSalvarEventoSemAssociarConteudosQuandoListaEstaVazia() throws Exception {
        // Arrange: prepara um evento sem conteúdos selecionados.
        LocalDateTime inicio = LocalDateTime.of(2026, 10, 21, 14, 0);
        LocalDateTime fim = inicio.plusHours(1);
        Evento evento = new Evento("Encontro da turma", inicio, fim, null);
        evento.setIdTurma(20L);
        evento.setIdProfessor(30L);

        Evento eventoSalvo = new Evento("Encontro da turma", inicio, fim, null);
        eventoSalvo.setIdTurma(20L);
        eventoSalvo.setIdProfessor(30L);
        ReflectionTestUtils.setField(eventoSalvo, "id", 11L);

        List<Long> idsConteudos = List.of();
        when(eventoRepository.save(evento)).thenReturn(eventoSalvo);
        when(eventoDetalhesDAO.buscarNomeTurma(20L)).thenReturn("Engenharia de Software");
        when(eventoDetalhesDAO.buscarConteudosDoEvento(11L)).thenReturn(List.of());

        // Act: salva o evento passando uma lista vazia.
        Evento resultado = eventoService.adicionarEvento(evento, idsConteudos);

        // Assert: o evento é salvo normalmente, sem solicitar associações.
        assertAll(
                () -> assertSame(eventoSalvo, resultado),
                () -> assertEquals("Engenharia de Software", resultado.getNomeTurma()),
                () -> assertEquals(List.of(), resultado.getNomesConteudos())
        );
        verify(eventoRepository).save(evento);
        verify(eventoConteudoDAO, never()).associarConteudos(any(), any());
    }
}
