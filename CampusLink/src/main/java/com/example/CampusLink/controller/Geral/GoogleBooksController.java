package com.example.CampusLink.controller.Geral;

import com.example.CampusLink.dto.GoogleBookDTO;
import com.example.CampusLink.service.GoogleBooksService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.regex.Pattern;

@RestController
public class GoogleBooksController {

    private static final Logger logger = LoggerFactory.getLogger(GoogleBooksController.class);
    private static final Pattern VOLUME_ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,128}");
    private final GoogleBooksService googleBooksService;

    public GoogleBooksController(GoogleBooksService googleBooksService) {
        this.googleBooksService = googleBooksService;
    }
    @Operation(
            tags = {"Google Books"},
            summary = "Buscar livros",
            description = """
                            Consulta a Google Books API por meio do endpoint
                            GET https://www.googleapis.com/books/v1/volumes.
                    
                            O backend envia o termo q, solicita até 10 resultados por página
                            e utiliza a chave configurada em GOOGLE_BOOKS_API_KEY.
                            A paginação opcional usa startIndex e informa o próximo índice
                            pelo cabeçalho X-Next-Start-Index.
                    
                            Retorna informações bibliográficas para ajudar o professor
                            a selecionar materiais acadêmicos para a turma.
                            A resposta pode incluir o cabeçalho X-Next-Start-Index,
                            que deve ser enviado como startIndex para carregar a página seguinte.
                            A busca não salva conteúdos automaticamente.
                    
                            Para consultar a API externa, a sessão deve conter usuarioLogado.
                            Sem usuário logado, a implementação atual retorna HTTP 200
                            com uma lista vazia, sem consultar o Google Books.
                            ""\",
        """,
            parameters = {
                    @Parameter(
                            name = "q",
                            in = ParameterIn.QUERY,
                            required = true,
                            description = "Termo utilizado na busca de livros",
                            example = "engenharia de software"
                    ),
                    @Parameter(
                            name = "startIndex",
                            in = ParameterIn.QUERY,
                            description = "Índice retornado no cabeçalho X-Next-Start-Index para carregar os próximos livros"
                    )
            }
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Lista com até 10 livros. O cabeçalho X-Next-Start-Index, quando presente, permite carregar a próxima página. Pode retornar uma lista vazia quando não há resultados úteis ou usuário logado.",
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(
                                    schema = @Schema(
                                            implementation = GoogleBookDTO.class
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
                    responseCode = "429",
                    description = "O Google Books limitou temporariamente as buscas; aguarde e tente novamente.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(type = "object")
                    )
            ),
            @ApiResponse(
                    responseCode = "502",
                    description = "O Google Books recusou a consulta, por exemplo por uma chave ou configuração inválida.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(type = "object")
                    )
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = "O Google Books ou a conexão externa está temporariamente indisponível.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(type = "object")
                    )
            )
    })
    @GetMapping("/googlebooks/buscar")
    @ResponseBody
    public ResponseEntity<?> buscar(
            @RequestParam("q") String termo,
            @RequestParam(value = "startIndex", defaultValue = "0") int startIndex,
            HttpSession session) {

        if (session.getAttribute("usuarioLogado") == null) {
            return ResponseEntity.ok(List.of());
        }

        try {
            GoogleBooksService.PaginaLivros pagina = googleBooksService.buscarLivros(termo, startIndex);
            HttpHeaders headers = new HttpHeaders();
            if (pagina.nextStartIndex() != null) {
                headers.set("X-Next-Start-Index", pagina.nextStartIndex().toString());
            }
            return ResponseEntity.ok().headers(headers).body(pagina.livros());
        } catch (RestClientResponseException e) {
            int statusProvedor = e.getStatusCode().value();
            logger.warn("Google Books respondeu com HTTP {} durante a busca.", statusProvedor);

            if (statusProvedor == 429) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(Map.of("message", "O Google Books limitou temporariamente as buscas. Aguarde um pouco e tente novamente."));
            }

            if (e.getStatusCode().is5xxServerError()) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(Map.of("message", "O Google Books está temporariamente indisponível. Tente novamente em alguns instantes."));
            }

            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("message", "Não foi possível consultar o Google Books. Verifique a configuração da integração ou tente novamente."));
        } catch (ResourceAccessException e) {
            logger.warn("Não foi possível conectar ao Google Books: {}.", e.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message", "Não foi possível conectar ao Google Books. Verifique sua conexão e tente novamente."));
        }
    }

    @GetMapping("/googlebooks/detalhes")
    @ResponseBody
    public ResponseEntity<?> detalhes(
            @RequestParam("id") String volumeId,
            HttpSession session) {

        if (session.getAttribute("usuarioLogado") == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "É necessário entrar para consultar os detalhes do livro."));
        }
        if (!VOLUME_ID_PATTERN.matcher(volumeId).matches()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "O identificador do livro é inválido."));
        }

        try {
            GoogleBookDTO livro = googleBooksService.buscarDetalhesLivro(volumeId);
            if (livro == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(livro);
        } catch (RestClientResponseException e) {
            int statusProvedor = e.getStatusCode().value();
            logger.warn("Google Books respondeu com HTTP {} ao consultar detalhes do volume.", statusProvedor);

            if (statusProvedor == 429) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(Map.of("message", "O Google Books limitou temporariamente as consultas. Tente novamente mais tarde."));
            }
            if (e.getStatusCode().is5xxServerError()) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(Map.of("message", "O Google Books está temporariamente indisponível."));
            }
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("message", "Não foi possível consultar os detalhes deste livro."));
        } catch (ResourceAccessException e) {
            logger.warn("Não foi possível conectar ao Google Books para consultar os detalhes do volume.");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message", "Não foi possível conectar ao Google Books."));
        }
    }
}
