// verificacao dos chamados confere quem pode ver responder e concluir um chamado do canal de ajuda
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// testes dos chamados esta classe simula administradores e usuarios acessando chamados proprios e alheios
class ChamadoServiceTests {

    private ChamadoRepository repositorio;
    private ChamadoService chamados;
    private DadosUsuarioDTO admin;
    private DadosUsuarioDTO aluno;

    // preparar testes dos chamados este metodo cria o servico um administrador e um aluno
    @BeforeEach
    void preparar() {
        repositorio = mock(ChamadoRepository.class);
        chamados = new ChamadoService(repositorio);
        admin = usuario(1L, "ADMINISTRADOR");
        aluno = usuario(2L, "ALUNOS");
    }

    // testar chamado alheio este teste confere que um usuario nao abre o chamado de outra pessoa
    @Test
    void naoMostraChamadoDeOutroUsuario() {
        // preparar banco sem chamado 50 para o aluno
        when(repositorio.buscarDoUsuario(50L, aluno.getId())).thenReturn(null);

        // executar consulta do chamado
        ResponseStatusException erro = assertThrows(ResponseStatusException.class,
                () -> chamados.buscarDoUsuario(50L, aluno));

        // verificar resposta de nao encontrado sem revelar o chamado
        assertEquals(HttpStatus.NOT_FOUND, erro.getStatusCode());
    }

    // testar resposta sem perfil administrativo este teste confere que um aluno nao responde como equipe de suporte
    @Test
    void recusaRespostaDeQuemNaoEhAdministrador() {
        // preparar aluno tentando responder

        // executar tentativa de resposta
        ResponseStatusException erro = assertThrows(ResponseStatusException.class,
                () -> chamados.responder(50L, aluno, "Resposta indevida"));

        // verificar acesso proibido e nada gravado
        assertEquals(HttpStatus.FORBIDDEN, erro.getStatusCode());
        verify(repositorio, never()).inserirResposta(anyLong(), anyLong(), any());
    }

    // testar chamado concluido este teste confere que um chamado encerrado nao recebe novas respostas
    @Test
    void recusaRespostaEmChamadoConcluido() {
        // preparar chamado ja concluido
        when(repositorio.buscarPorId(50L, true)).thenReturn(chamado(50L, "concluido", List.of()));

        // executar tentativa de resposta do administrador
        assertThrows(IllegalStateException.class, () -> chamados.responder(50L, admin, "Nova resposta"));

        // verificar que nenhuma resposta foi gravada
        verify(repositorio, never()).inserirResposta(anyLong(), anyLong(), any());
    }

    // testar conclusao sem resposta este teste confere que o administrador precisa responder antes de concluir
    @Test
    void exigeRespostaDoAdministradorAntesDeConcluir() {
        // preparar chamado aberto apenas com mensagem do usuario
        when(repositorio.buscarPorId(50L, true)).thenReturn(chamado(50L, "aberto", List.of()));
        when(repositorio.listarRespostas(50L)).thenReturn(List.of(resposta("usuario")));

        // executar tentativa de conclusao
        assertThrows(IllegalStateException.class, () -> chamados.concluir(50L, admin));

        // verificar que o chamado continua aberto
        verify(repositorio, never()).concluir(anyLong(), anyLong());
    }

    // montar usuario do teste este metodo cria um usuario com id e perfil
    private DadosUsuarioDTO usuario(Long id, String perfil) {
        DadosUsuarioDTO usuario = new DadosUsuarioDTO();
        usuario.setId(id);
        usuario.setPerfil(perfil);
        return usuario;
    }

    // montar chamado do teste este metodo cria um chamado com situacao e respostas
    private ChamadoDTO chamado(Long id, String status, List<ChamadoRespostaDTO> respostas) {
        ChamadoDTO chamado = new ChamadoDTO();
        chamado.setId(id);
        chamado.setStatus(status);
        chamado.setRespostas(new ArrayList<>(respostas));
        return chamado;
    }

    // montar resposta do teste este metodo cria uma mensagem do usuario ou do administrador
    private ChamadoRespostaDTO resposta(String tipoAutor) {
        ChamadoRespostaDTO resposta = new ChamadoRespostaDTO();
        resposta.setTipoAutor(tipoAutor);
        return resposta;
    }
}
