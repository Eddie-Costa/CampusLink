// pesquisa de videos disponibiliza buscas e detalhes para a interface
package com.example.CampusLink.controller.Geral;

import com.example.CampusLink.dto.YoutubeVideoDTO;
import com.example.CampusLink.service.YoutubeService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

// controle de videos esta classe verifica a sessao e confere os identificadores antes das consultas
@RestController
public class YoutubeController {

    private static final Pattern VIDEO_ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]{11}");
    private static final int MAX_VIDEO_IDS_POR_REQUISICAO = 50;
    private final YoutubeService youtubeService;

    // preparar consulta de videos este construtor recebe o servico de busca externa
    public YoutubeController(YoutubeService youtubeService) {
        this.youtubeService = youtubeService;
    }
    // buscar videos pela tela este metodo consulta os resultados e informa a proxima pagina
    @Operation(
            tags = {"YouTube"},
            summary = "Buscar vídeos",
            description = """
                    Consulta a YouTube Data API para pesquisar materiais acadêmicos.
                    
                            A primeira chamada utiliza:
                            GET https://www.googleapis.com/youtube/v3/search
                            com part=snippet, type=video, maxResults=10 e o termo q.
                    
                            Quando existem resultados, uma segunda chamada utiliza:
                            GET https://www.googleapis.com/youtube/v3/videos
                            com part=contentDetails e os identificadores encontrados,
                            para obter a duração de cada vídeo.
                    
                            As duas chamadas utilizam a chave configurada no backend
                            pela variável YOUTUBE_API_KEY.
                    
                            Retorna identificador, título, canal, miniatura e duração.
                            A duração é convertida para M:SS ou H:MM:SS.
                            A resposta pode incluir o cabeçalho X-Next-Page-Token,
                            que deve ser enviado como pageToken para carregar a página seguinte.
                            A busca não salva conteúdos automaticamente.
                    
                            Para consultar a API externa, a sessão deve conter usuarioLogado.
                            Sem usuário logado, a implementação atual retorna HTTP 200
                            com uma lista vazia, sem consultar o YouTube.
        """,
            parameters = {
                    @Parameter(
                            name = "q",
                            in = ParameterIn.QUERY,
                            required = true,
                            description = "Termo utilizado na busca de vídeos",
                            example = "engenharia de software"
                    ),
                    @Parameter(
                            name = "pageToken",
                            in = ParameterIn.QUERY,
                            description = "Token retornado no cabeçalho X-Next-Page-Token para carregar os próximos vídeos"
                    )
            }
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Lista com até 10 vídeos. O cabeçalho X-Next-Page-Token, quando presente, permite carregar a próxima página. Pode retornar uma lista vazia quando não há resultados ou usuário logado.",
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(
                                    schema = @Schema(
                                            implementation = YoutubeVideoDTO.class
                                    )
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "O parâmetro obrigatório q não foi enviado. O controller não possui validação específica para texto vazio.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(
                                    type = "object",
                                    description = "Resposta padrão de erro do Spring Boot, conforme a configuração da aplicação."
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Falha não tratada na pesquisa de vídeos, na consulta de duração ou no processamento dos dados. O status do provedor não é repassado diretamente.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(
                                    type = "object",
                                    description = "Resposta padrão de erro da aplicação; não há um DTO de erro específico desta integração."
                            )
                    )
            )
    })
    @GetMapping("/youtube/buscar")
    @ResponseBody
    public ResponseEntity<List<YoutubeVideoDTO>> buscar(
            @RequestParam("q") String termo,
            @RequestParam(value = "pageToken", required = false) String pageToken,
            HttpSession session) {

        // proteger consulta externa retorna uma lista vazia quando nao existe usuario conectado
        if (session.getAttribute("usuarioLogado") == null) {
            return ResponseEntity.ok(List.of());
        }

        YoutubeService.PaginaVideos pagina = youtubeService.buscarVideos(termo, pageToken);
        HttpHeaders headers = new HttpHeaders();
        // indicar mais videos inclui o codigo da proxima pagina quando o provedor o informa
        if (pagina.nextPageToken() != null && !pagina.nextPageToken().isBlank()) {
            headers.set("X-Next-Page-Token", pagina.nextPageToken());
        }
        return ResponseEntity.ok().headers(headers).body(pagina.videos());
    }

    // buscar detalhes dos videos pela tela este metodo confere os codigos e consulta os videos escolhidos
    @GetMapping("/youtube/detalhes")
    public ResponseEntity<List<YoutubeVideoDTO>> detalhes(
            @RequestParam("ids") List<String> videoIds,
            HttpSession session) {

        // proteger consulta externa retorna uma lista vazia quando nao existe usuario conectado
        if (session.getAttribute("usuarioLogado") == null) {
            return ResponseEntity.ok(List.of());
        }

        List<String> idsUnicos = videoIds.stream()
                .distinct()
                .collect(Collectors.toList());
        // validar consulta de videos recusa lista vazia quantidade excessiva ou codigos fora do formato
        if (idsUnicos.isEmpty()
                || idsUnicos.size() > MAX_VIDEO_IDS_POR_REQUISICAO
                || idsUnicos.stream().anyMatch(id -> !VIDEO_ID_PATTERN.matcher(id).matches())) {
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok(youtubeService.buscarDetalhesVideos(idsUnicos));
    }
}
