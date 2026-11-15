// verificacao do fluxo dos chamados confere abertura com textos validos filtros do administrador conclusao e mensagens do usuario
package com.example.CampusLink.service;

import com.example.CampusLink.dto.ChamadoDTO;
import com.example.CampusLink.dto.ChamadoRespostaDTO;
import com.example.CampusLink.dto.DadosUsuarioDTO;
import com.example.CampusLink.repository.ChamadoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// testes do fluxo dos chamados esta classe simula o banco com um chamado aberto do aluno 2
class ChamadoFluxoTests {

    private ChamadoRepository repositorio;
    private ChamadoService chamados;
    private DadosUsuarioDTO aluno;
    private DadosUsuarioDTO admin;

    // preparar testes do fluxo este metodo cria o servico um aluno um administrador e o chamado 15 aberto
    @BeforeEach
    void preparar() {
        repositorio = mock(ChamadoRepository.class);
        chamados = new ChamadoService(repositorio);
        aluno = usuario(2L, "ALUNOS");
        admin = usuario(1L, "ADMINISTRADOR");
        when(repositorio.buscarDoUsuario(15L, 2L)).thenReturn(chamado("aberto"));
        when(repositorio.buscarDoUsuario(15L, 2L, true)).thenReturn(chamado("aberto"));
        when(repositorio.buscarPorId(15L, false)).thenReturn(chamado("concluido"));
    }

    // testar abertura este teste confere assunto e descricao sem espacos e o chamado devolvido ao usuario
    @Test
    void abreChamadoComTextosLimpos() {
        // preparar formulario e codigo gerado
        ChamadoDTO formulario = new ChamadoDTO();
        formulario.setAssunto("  Erro no envio  ");
        formulario.setDescricao("  Nao consigo enviar  ");
        when(repositorio.inserir(2L, "Erro no envio", "Nao consigo enviar")).thenReturn(15L);

        // executar abertura
        ChamadoDTO aberto = chamados.abrir(aluno, formulario);

        // verificar chamado aberto
        assertEquals(15L, aberto.getId());
    }

    // testar textos invalidos este teste confere campo vazio texto longo e usuario sem sessao
    @Test
    void recusaChamadoComTextoInvalidoOuSemUsuario() {
        // preparar formularios invalidos
        ChamadoDTO semAssunto = new ChamadoDTO();
        semAssunto.setAssunto(" ");
        semAssunto.setDescricao("texto");
        ChamadoDTO assuntoLongo = new ChamadoDTO();
        assuntoLongo.setAssunto("a".repeat(151));
        assuntoLongo.setDescricao("texto");

        // executar aberturas e verificar mensagens
        assertEquals("Preencha o campo: O assunto.", assertThrows(IllegalArgumentException.class, () -> chamados.abrir(aluno, semAssunto)).getMessage());
        assertTrue(assertThrows(IllegalArgumentException.class, () -> chamados.abrir(aluno, assuntoLongo)).getMessage().contains("150 caracteres"));
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class, () -> chamados.abrir(null, semAssunto)).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class, () -> chamados.listarDoUsuario(new DadosUsuarioDTO())).getStatusCode());
        verify(repositorio, never()).inserir(anyLong(), anyString(), anyString());
    }

    // testar filtros do administrador este teste confere situacao valida situacao invalida e perfil sem permissao
    @Test
    void filtraChamadosDoAdministrador() {
        // preparar chamados abertos
        when(repositorio.listarParaAdmin("aberto")).thenReturn(List.of(chamado("aberto")));

        // executar filtros
        List<ChamadoDTO> abertos = chamados.listarParaAdmin(admin, "aberto");
        ResponseStatusException invalida = assertThrows(ResponseStatusException.class, () -> chamados.listarParaAdmin(admin, "apagado"));
        ResponseStatusException semPermissao = assertThrows(ResponseStatusException.class, () -> chamados.listarParaAdmin(aluno, ""));

        // verificar resultado e recusas
        assertEquals(1, abertos.size());
        assertEquals(HttpStatus.BAD_REQUEST, invalida.getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, semPermissao.getStatusCode());
    }

    // testar consultas com respostas este teste confere que usuario e administrador recebem o historico do chamado
    @Test
    void carregaRespostasNasConsultas() {
        // preparar historico com uma resposta
        when(repositorio.listarRespostas(15L)).thenReturn(List.of(resposta("admin")));

        // executar consultas
        ChamadoDTO doUsuario = chamados.buscarDoUsuario(15L, aluno);
        ChamadoDTO doAdmin = chamados.buscarParaAdmin(15L, admin);
        chamados.listarDoUsuario(aluno);

        // verificar historico carregado
        assertEquals(1, doUsuario.getRespostas().size());
        assertEquals(1, doAdmin.getRespostas().size());
        verify(repositorio).listarDoUsuario(2L);
    }

    // testar conclusao este teste confere a conclusao apos resposta e o chamado ja concluido sem nova gravacao
    @Test
    void concluiChamadoRespondidoUmaVez() {
        // preparar chamado respondido e chamado 16 ja concluido
        when(repositorio.buscarPorId(15L, true)).thenReturn(chamado("respondido"));
        when(repositorio.listarRespostas(15L)).thenReturn(List.of(resposta("admin")));
        when(repositorio.buscarPorId(16L, true)).thenReturn(chamado("concluido"));

        // executar conclusoes
        ChamadoDTO concluido = chamados.concluir(15L, admin);
        ChamadoDTO repetido = chamados.concluir(16L, admin);

        // verificar gravacao unica
        assertEquals("concluido", concluido.getStatus());
        assertNull(repetido);
        verify(repositorio).concluir(15L, 1L);
        verify(repositorio, never()).concluir(16L, 1L);
    }

    // testar resposta e mensagem este teste confere resposta do administrador e mensagem do usuario em chamado aberto
    @Test
    void registraRespostaEMensagemEmChamadoAberto() {
        // preparar chamado aberto para o administrador
        when(repositorio.buscarPorId(15L, true)).thenReturn(chamado("aberto"));

        // executar resposta e mensagem
        chamados.responder(15L, admin, "  Resolvido  ");
        chamados.enviarMensagemUsuario(15L, aluno, "  Obrigado  ");

        // verificar textos gravados
        verify(repositorio).inserirResposta(15L, 1L, "Resolvido");
        verify(repositorio).inserirMensagemUsuario(15L, 2L, "Obrigado");
    }

    // testar chamado encerrado ou inexistente este teste confere a recusa de mensagem em chamado concluido ou de outra pessoa
    @Test
    void recusaMensagemEmChamadoConcluidoOuInexistente() {
        // preparar chamado concluido e chamado inexistente
        when(repositorio.buscarDoUsuario(15L, 2L, true)).thenReturn(chamado("concluido"));

        // executar mensagens
        IllegalStateException concluido = assertThrows(IllegalStateException.class, () -> chamados.enviarMensagemUsuario(15L, aluno, "Oi"));
        ResponseStatusException inexistente = assertThrows(ResponseStatusException.class, () -> chamados.enviarMensagemUsuario(99L, aluno, "Oi"));
        ResponseStatusException semChamado = assertThrows(ResponseStatusException.class, () -> chamados.responder(99L, admin, "Oi"));

        // verificar recusas
        assertTrue(concluido.getMessage().contains("Abra outro chamado"));
        assertEquals(HttpStatus.NOT_FOUND, inexistente.getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, semChamado.getStatusCode());
        verify(repositorio, never()).inserirMensagemUsuario(anyLong(), anyLong(), anyString());
    }

    // montar usuario este metodo cria o usuario com codigo e perfil
    private DadosUsuarioDTO usuario(Long id, String perfil) {
        DadosUsuarioDTO usuario = new DadosUsuarioDTO();
        usuario.setId(id);
        usuario.setPerfil(perfil);
        return usuario;
    }

    // montar chamado este metodo cria o chamado 15 com a situacao informada
    private ChamadoDTO chamado(String status) {
        ChamadoDTO chamado = new ChamadoDTO();
        chamado.setId(15L);
        chamado.setStatus(status);
        chamado.setRespostas(new ArrayList<>());
        return chamado;
    }

    // montar resposta este metodo cria uma mensagem do autor informado
    private ChamadoRespostaDTO resposta(String tipoAutor) {
        ChamadoRespostaDTO resposta = new ChamadoRespostaDTO();
        resposta.setTipoAutor(tipoAutor);
        return resposta;
    }
}
