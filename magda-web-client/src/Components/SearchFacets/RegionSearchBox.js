import React, { Component } from "react";
import "./RegionSearchBox.css";
import debounce from "lodash.debounce";
import Form from "muicss/lib/react/form";
import Input from "muicss/lib/react/input";
import search from "../../assets/search-dark.svg";

/**
 * Searchbox for facet facet
 */
class RegionSearchBox extends Component {
    constructor(props) {
        super(props);
        this.onSearchTextChange = this.onSearchTextChange.bind(this);
        this.clearSearch = this.clearSearch.bind(this);
        this.handleKeyDown = this.handleKeyDown.bind(this);
        this.onExcKeyDown = this.onExcKeyDown.bind(this);
        this.onClick = this.onClick.bind(this);
        this.debounceSearchFacet = debounce(this.searchFacet, 200);

        /**
         * @type {object}
         * @property {string} searchText the user input when doing search
         * @property {number} indexOfOptionOnFocus the index of option on focus when using keyboard up and down keys to navigate
         */
        this.state = {
            searchText: "",
            indexOfOptionOnFocus: -1
        };
    }

    componentDidMount() {
        // when esc key is pressed at anytime, clear search box and close the search result list
        window.addEventListener("keydown", this.onExcKeyDown);
    }

    componentWillUnmount() {
        this.props.searchBoxValueChange("");
        window.removeEventListener("keydown", this.onExcKeyDown);
    }

    onClick(option, event) {
        this.props.onToggleOption(option);
        this.clearSearch();
    }

    onExcKeyDown(event) {
        if (event.which === 27) {
            this.clearSearch();
        }
    }

    handleKeyDown(e) {
        if (e.keyCode === 38) {
            e.preventDefault();
            this.move("up");
        }

        if (e.keyCode === 40) {
            e.preventDefault();
            this.move("down");
        }

        if (e.keyCode === 13) {
            if (e.target.tagName === "INPUT") e.preventDefault();
            return false;
        }
    }

    move(direction) {
        let totalNumberOfItemsToNavigate = this.props.options.length;
        let current = this.state.indexOfOptionOnFocus;
        let next;
        let previous;

        if (direction === "up") {
            if (0 < current) {
                previous = current - 1;
            } else {
                previous = totalNumberOfItemsToNavigate - 1;
            }
            this.setState({
                indexOfOptionOnFocus: previous
            });
        }

        if (direction === "down") {
            if (current < totalNumberOfItemsToNavigate - 1) {
                next = current + 1;
            } else {
                next = 0;
            }
            this.setState({
                indexOfOptionOnFocus: next
            });
        }
    }

    onSearchTextChange(e) {
        // when the search text is updated, we need to reset the index
        this.setState({
            searchText: e.target.value,
            indexOfOptionOnFocus: -1
        });
        this.debounceSearchFacet(e.target.value);
        this.props.searchBoxValueChange(e.target.value);
    }

    searchFacet(text) {
        this.props.searchFacet(text);
    }

    clearSearch() {
        this.setState({
            searchText: ""
        });
        this.props.searchBoxValueChange("");
    }

    render() {
        return (
            <div className="region-search-box  facet-search-box">
                <Form onKeyDown={this.handleKeyDown}>
                    <img className="search-icon" src={search} alt="search" />
                    <Input
                        type="text"
                        value={this.state.searchText}
                        onInput={this.onSearchTextChange}
                        placeholder="Search by Region"
                    />
                </Form>
                {this.state.searchText.length > 0 && (
                    <ul
                        className="region-search-box__options mui-list--unstyled"
                        onKeyDown={this.handleKeyDown}
                    >
                        {this.props.options.map((option, i) => (
                            <li key={`${option.value}-${i}`}>
                                {this.props.renderOption(
                                    option,
                                    this.onClick,
                                    null,
                                    this.state.indexOfOptionOnFocus === i
                                )}
                            </li>
                        ))}
                    </ul>
                )}
            </div>
        );
    }
}

RegionSearchBox.defaultProps = { options: [] };

export default RegionSearchBox;
