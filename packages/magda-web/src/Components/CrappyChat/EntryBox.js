import React from "react";
import Editor from "draft-js-plugins-editor";
import {
  EditorState,
  ContentState,
  convertFromRaw,
  convertToRaw
} from "draft-js";
import base from "./Base";

import pluginsFn from "./Plugins/Plugins";
import PluginComponents from "./Plugins/PluginComponents";

import "./EntryBox.css";

export default class EntryBox extends React.Component {
  constructor(props) {
    super(props);

    this.plugins = pluginsFn();

    this.state = {
      editorState: EditorState.createEmpty(),
      users: []
    };
  }

  onEditorChange(newEditorState) {
    this.setState({
      editorState: newEditorState
    });
  }

  resetState() {
    this.setState({
      editorState: EditorState.push(
        this.state.editorState,
        ContentState.createFromText("")
      )
    });
  }

  onSubmit(e) {
    e.preventDefault();

    this.props.onSubmit(
      JSON.stringify(convertToRaw(this.state.editorState.getCurrentContent()))
    );

    this.resetState();
  }

  render() {
    return (
      <form className="entry-box" onSubmit={this.onSubmit.bind(this)}>
        <div className="entry-box__editor">
          <Editor
            editorState={this.state.editorState}
            onChange={this.onEditorChange.bind(this)}
            plugins={Object.values(this.plugins)}
          />
        </div>
        <PluginComponents
          userMentionsPlugin={this.plugins.userMentions}
          dataSetMentionsPlugin={this.plugins.dataSetMentions}
        />
        <input className="entry-box__submit-button" type="submit" />
      </form>
    );
  }
}
