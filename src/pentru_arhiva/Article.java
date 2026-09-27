package tema.apd;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

// clasa pentru reprezentarea unui articol. Mai jos am inclus momentan
// doar campurile obligatorii pt procesare. Pentru ca am vazut ca fisierele json din setul de date
// au extrem de multe atribute, campuri pe care nu e necesar sa le folosesc in cadrul temei.
@JsonIgnoreProperties(ignoreUnknown = true)
public class Article {
	private String uuid;
	private String title;
	private String author;
	private String url;
	private String text;
	private String published;
	private String language;
	private List<String> categories;


	public String getUuid() {
		return uuid;
	}

	public void setUuid(String uuid) {
		this.uuid = uuid;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getAuthor() {
		return author;
	}

	public void setAuthor(String author) {
		this.author = author;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

	public String getPublished() {
		return published;
	}

	public void setPublished(String published) {
		this.published = published;
	}

	public String getLanguage() {
		return language;
	}

	public void setLanguage(String language) {
		this.language = language;
	}

	public List<String> getCategories() {
		return categories;
	}

	public void setCategories(List<String> categories) {
		this.categories = categories;
	}

	@Override
	public String toString() {
		return this.published + " " + this.url;
	}
}

