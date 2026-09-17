from app.text_processing import normalize_speech_text, semantic_segments


def test_normalizes_markup_urls_controls_and_replacement_characters():
    result = normalize_speech_text(
        "- 查看[路况](https://example.test/a)。\ufffd\x00 **结果**：`G205` & 正常。"
    )
    assert result.text == "查看路况。结果：G205和正常。"
    assert "http" not in result.text
    assert "\ufffd" not in result.text
    assert result.replacements >= 4


def test_semantic_segments_preserve_business_tokens():
    text = (
        "G205公路K123+456附近发生异常，监测值31.495 mm，利用率80%，"
        "采集时间为2026年9月15日09:30，请立即安排人员核查现场并反馈。"
    )
    chunks = semantic_segments(text, maximum=35, minimum=16)
    assert "".join(chunks) == normalize_speech_text(text).text
    for token in ("G205", "K123+456", "31.495 mm", "80%", "2026年9月15日", "九点三十分"):
        assert any(token in chunk for chunk in chunks)
    assert all(len(chunk) <= 35 for chunk in chunks)


def test_semantic_segments_prefer_complete_sentences():
    text = "当前道路通行正常。请继续关注设施状态！如有变化，系统会及时提示。"
    assert semantic_segments(text, maximum=30) == [
        "当前道路通行正常。请继续关注设施状态！",
        "如有变化，系统会及时提示。",
    ]


def test_emphasis_is_removed_when_following_cjk_text():
    # Regression: the previous (?<!\w) lookbehind failed after CJK characters,
    # so "执行**预案**" kept literal asterisks that the engine would vocalise.
    assert normalize_speech_text("执行**应急预案**。").text == "执行应急预案。"
    assert normalize_speech_text("并执行**应急预案**。").text == "并执行应急预案。"
    assert normalize_speech_text("__加粗__完成。").text == "加粗完成。"
    assert normalize_speech_text("*斜体*完成。").text == "斜体完成。"


def test_headings_rules_and_blockquotes_are_not_spoken():
    assert normalize_speech_text("# 标题").text == "标题"
    assert normalize_speech_text("## 二级标题").text.lstrip("#") == "二级标题"
    cleaned = normalize_speech_text("> 引用内容。").text
    assert ">" not in cleaned and "引用内容" in cleaned


def test_no_markdown_punctuation_survives_normalization():
    result = normalize_speech_text(
        "**粗体**、*斜体*、`代码`、~删除~、__下划线__ 与 ***三星*** 全部清理。"
    )
    for symbol in "*_`~":
        assert symbol not in result.text, f"{symbol!r} leaked into {result.text!r}"


def test_business_tokens_survive_markdown_cleanup():
    result = normalize_speech_text("**G205** 路段 `K123+456` 降雨 **31.495 mm** 达到 __80%__。")
    for token in ("G205", "K123+456", "31.495 mm", "80%"):
        assert token in result.text, f"{token!r} lost in {result.text!r}"


def test_time_intervals_and_seconds_are_spoken_explicitly():
    for separator in ('-', '–', '—', '~', '～', '至', '到'):
        assert normalize_speech_text(f'峰值05:00{separator}05:59。').text == '峰值五点整到五点五十九分。'
    assert normalize_speech_text('08:30:15').text == '八点三十分十五秒'
    assert normalize_speech_text('23:00—次日01:00').text == '二十三点整到次日一点整'
    assert normalize_speech_text('０５：００–０５：５９').text == '五点整到五点五十九分'


def test_dates_invalid_clocks_and_business_values():
    assert normalize_speech_text('2026-09-17 08:30').text == '二〇二六年九月十七日 八点三十分'
    assert normalize_speech_text('25:61').text == '25:61'
    assert normalize_speech_text('G104，FJ023，80%，1:2。').text == 'G104，FJ023，80%，1:2。'
    text = normalize_speech_text('05:00–05:59').text
    assert normalize_speech_text(text).text == text


def test_business_words_are_not_cut_at_length_boundary():
    text = '甲' * 31 + '城市对和跨市路线。'
    chunks = semantic_segments(text, maximum=33, minimum=16)
    assert ''.join(chunks) == normalize_speech_text(text).text
    assert any('城市对' in chunk for chunk in chunks)


def test_city_pair_terms_are_spoken_without_rewording():
    assert normalize_speech_text('共识别22个城市对和32条跨市路线。').text == '共识别22个城市对和32条跨市路线。'
    assert normalize_speech_text('二十二个城市对。').text == '二十二个城市对。'
    assert normalize_speech_text('城市对联系压力。').text == '城市对联系压力。'


def test_repeated_city_pairs_and_geographic_connections():
    assert normalize_speech_text('22个城市对，这些城市对联系紧密。').text == '22个城市对，这些城市对联系紧密。'
    for symbol in ('—', '–', '-', '→', '->', '⇒', '⟶', '➜'):
        assert normalize_speech_text(f'南平市{symbol}宁德市。').text == '南平市到宁德市。'
    assert normalize_speech_text('G357 东山-泸水，FJ014->FJ023。').text == 'G357 东山到泸水，FJ014到FJ023。'
    assert normalize_speech_text('南平市、宁德市，温度-5，2026-09-17。').text == '南平市、宁德市，温度-5，二〇二六年九月十七日。'
