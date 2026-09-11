package cn.fj.roadagent.interfaces.rest.speech;

import cn.fj.roadagent.application.speech.SpeechAudio;
import cn.fj.roadagent.application.speech.SpeechCapabilities;
import cn.fj.roadagent.application.speech.SpeechTranscription;
import cn.fj.roadagent.interfaces.rest.common.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SpeechControllerTest {
    @Test
    void shouldReturnJsonForSpeechErrorsEvenWhenClientAcceptsMp3() throws Exception {
        MockMvc failing = MockMvcBuilders.standaloneSetup(new SpeechController(
                () -> new SpeechCapabilities(true, true, "small", "Serena", 60, 10485760),
                command -> { throw new cn.fj.roadagent.application.exception.ExternalServiceException("SPEECH", "ASR_NO_SPEECH", "未识别到清晰语音"); },
                command -> { throw new cn.fj.roadagent.application.exception.ExternalServiceException("SPEECH", "TTS_BUSY", "语音服务正忙"); }
        )).setControllerAdvice(new GlobalExceptionHandler()).build();
        failing.perform(post("/api/v1/speech/syntheses").accept("audio/mpeg")
                .contentType("application/json").content("{\"text\":\"测试\"}"))
                .andExpect(status().isTooManyRequests()).andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.code").value("TTS_BUSY"));
        failing.perform(multipart("/api/v1/speech/transcriptions")
                .file(new MockMultipartFile("audio", "recording.webm", "audio/webm", new byte[]{1}))
                .param("durationMs", "1000"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("ASR_NO_SPEECH"));
    }
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new SpeechController(
                    () -> new SpeechCapabilities(true, true, "small", "Xiaoxiao", 60, 10485760),
                    command -> new SpeechTranscription("五四路现在拥堵吗", "zh", 1200),
                    command -> new SpeechAudio(new byte[]{1, 2, 3}, "audio/mpeg")
            ))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void shouldExposeCapabilitiesAndTranscription() throws Exception {
        mvc.perform(get("/api/v1/speech/capabilities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.asrAvailable").value(true))
                .andExpect(jsonPath("$.data.maxRecordingSeconds").value(60));

        MockMultipartFile audio = new MockMultipartFile(
                "audio", "recording.webm", "audio/webm", new byte[]{1, 2}
        );
        mvc.perform(multipart("/api/v1/speech/transcriptions")
                        .file(audio)
                        .param("durationMs", "1300"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.text").value("五四路现在拥堵吗"))
                .andExpect(jsonPath("$.data.language").value("zh"));
    }

    @Test
    void shouldReturnSynthesizedMp3() throws Exception {
        mvc.perform(post("/api/v1/speech/syntheses")
                        .contentType("application/json")
                        .content("{\"text\":\"道路通行正常。\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("audio/mpeg"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().bytes(new byte[]{1, 2, 3}));
    }
}
