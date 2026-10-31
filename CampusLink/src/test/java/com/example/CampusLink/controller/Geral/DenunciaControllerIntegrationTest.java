package com.example.CampusLink.controller.Geral;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DenunciaControllerIntegrationTest extends IntegracaoHttpBase {

    @Test
    void denunciar_comMotivoValido_deveRedirecionarEPersistirDenuncia() {
        var resposta = postar(aluno, rotaDenuncia(), formulario("motivo", "  Material incorreto  "));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(resposta.getHeaders().getLocation()).hasPath("/turmas/" + turmaId);
        conferirPagina(aluno, "/turmas/" + turmaId, "Denúncia registrada com sucesso.");
        var denuncia = jdbc.queryForMap("SELECT * FROM \"DENUNCIAS\" WHERE id_conteudo = ? AND id_aluno = ?",
                conteudoId, alunoId);
        assertThat(((Number) denuncia.get("id")).longValue()).isPositive();
        assertThat(denuncia.get("motivo")).isEqualTo("Material incorreto");
        assertThat(denuncia.get("status")).isEqualTo("pendente");
        assertThat(jdbc.queryForObject("SELECT status FROM \"CONTEUDOS\" WHERE id = ?", String.class, conteudoId))
                .isEqualTo("ativo");
    }

    @Test
    void denunciar_semParametroMotivo_deveRetornar400SemPersistir() {
        var resposta = postar(aluno, rotaDenuncia(), formulario());

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody()).contains("\"status\":400", "Bad Request", rotaDenuncia());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM \"DENUNCIAS\" WHERE id_conteudo = ?", Long.class, conteudoId))
                .isZero();
    }

    @Test
    void denunciar_aoAtingirLimite_deveSuspenderConteudoEBloquearDownload() throws Exception {
        long arquivoId = criarArquivo();
        var primeira = postar(aluno, rotaDenuncia(), formulario("motivo", "Material incorreto"));
        assertThat(primeira.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        conferirPagina(aluno, "/turmas/" + turmaId, "Denúncia registrada com sucesso.");

        var segunda = postar(outroAluno, rotaDenuncia(), formulario("motivo", "Material desatualizado"));

        assertThat(segunda.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        conferirPagina(outroAluno, "/turmas/" + turmaId, "Conteúdo suspenso para análise.");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM \"DENUNCIAS\" WHERE id_conteudo = ?", Long.class, conteudoId))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT status FROM \"CONTEUDOS\" WHERE id = ?", String.class, conteudoId))
                .isEqualTo("suspenso_denuncia");
        var download = aluno.get().uri("/arquivos/" + arquivoId + "/download").retrieve().toEntity(byte[].class);
        assertThat(download.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(download.getBody()).isNullOrEmpty();
        assertThat(arquivos.buscarPorId(arquivoId).getIdConteudo()).isEqualTo(conteudoId);
        verify(storage, never()).download(anyString());
    }
}
