package com.example.CampusLink.controller.Geral;

import com.example.CampusLink.dto.YoutubeVideoDTO;
import com.example.CampusLink.service.YoutubeService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import java.util.List;
import java.util.Map;

@RestController
public class YoutubeController {

    private final YoutubeService youtubeService;

    public YoutubeController(YoutubeService youtubeService) {
        this.youtubeService = youtubeService;
    }
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
                    )
            }
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Lista de até 10 vídeos. Pode retornar uma lista vazia quando não há resultados ou usuário logado.",
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
    public List<YoutubeVideoDTO> buscar(
            @RequestParam("q") String termo,
            HttpSession session) {

        if (session.getAttribute("usuarioLogado") == null) {
            return List.of();
        }

        return youtubeService.buscarVideos(termo);
    }

        @ExceptionHandler(MissingServletRequestParameterException.class)
        public ResponseEntity<Map<String, String>> parametroObrigatorioAusente(
                        MissingServletRequestParameterException excecao) {

                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                                "error", "Parâmetro obrigatório ausente.",
                                "parameter", excecao.getParameterName()
                ));
        }
}
