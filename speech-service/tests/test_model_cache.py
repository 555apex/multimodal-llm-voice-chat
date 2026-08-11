from io import BytesIO

from app import model_cache


class FakeResponse(BytesIO):
    status = 200
    headers = {"Content-Length": "12"}

    def __enter__(self) -> "FakeResponse":
        return self

    def __exit__(self, *_: object) -> None:
        self.close()


def test_downloads_model_file_atomically_without_external_network(
    tmp_path, monkeypatch
) -> None:
    monkeypatch.setattr(
        model_cache,
        "urlopen",
        lambda *_args, **_kwargs: FakeResponse(b"model-bytes!"),
    )
    destination = tmp_path / "model.bin"

    model_cache._download_with_resume("https://model.invalid/model.bin", destination)

    assert destination.read_bytes() == b"model-bytes!"
    assert not (tmp_path / "model.bin.part").exists()
