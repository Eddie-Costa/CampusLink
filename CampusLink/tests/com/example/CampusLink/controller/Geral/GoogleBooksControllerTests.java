package com.example.CampusLink.controller.Geral;

import com.example.CampusLink.dto.GoogleBookDTO;
import com.example.CampusLink.service.GoogleBooksService;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GoogleBooksControllerTests {

    @Test
    void usuarioLogadoBuscaLivrosERecebeResultadosDoServico() {
        GoogleBooksService googleBooksService = mock(GoogleBooksService.class);
        GoogleBooksController controller = new GoogleBooksController(googleBooksService);
        HttpSession session = mock(HttpSession.class);
        GoogleBookDTO livro = new GoogleBookDTO();
        livro.setTitulo("Engenharia de software");
        List<GoogleBookDTO> resultados = List.of(livro);

        when(session.getAttribute("usuarioLogado")).thenReturn(new Object());
        when(googleBooksService.buscarLivros("engenharia de software")).thenReturn(resultados);

        assertEquals(resultados, controller.buscar("engenharia de software", session));

        verify(googleBooksService).buscarLivros("engenharia de software");
    }

    @Test
    void visitanteRecebeListaVaziaSemConsultarGoogleBooks() {
        GoogleBooksService googleBooksService = mock(GoogleBooksService.class);
        GoogleBooksController controller = new GoogleBooksController(googleBooksService);
        HttpSession session = mock(HttpSession.class);

        assertTrue(controller.buscar("engenharia de software", session).isEmpty());

        verify(googleBooksService, never()).buscarLivros(anyString());
    }
}
