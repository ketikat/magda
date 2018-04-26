import React, { Component } from "react";
import TemporalAspectViewer from "../../UI/TemporalAspectViewer";
import DatasetPreview from "./DatasetPreview";
import DescriptionBox from "../../UI/DescriptionBox";
import MarkdownViewer from "../../UI/MarkdownViewer";
import StarRating from "../../UI/StarRating";
import TagsBox from "../../UI/TagsBox";
import { connect } from "react-redux";
import DistributionRow from "../DistributionRow";
import queryString from "query-string";
import "./RecordDetails.css";
import "./DatasetDetails.css";
import { Link } from "react-router-dom";
import { Small, Medium } from "../../UI/Responsive";

class DatasetDetails extends Component {
    state = {
        showPreview: false
    };
    render() {
        const dataset = this.props.dataset;
        const datasetId = this.props.match.params.datasetId;
        const searchText =
            queryString.parse(this.props.location.search).q || "";
        const source = `This dataset was originally found on [${
            this.props.dataset.source
        }](${dataset.landingPage})`;
        return (
            <div className="dataset-details">
                <div className="dataset-details-overview">
                    <Small>
                        <DescriptionBox
                            content={dataset.description}
                            truncateLength={200}
                        />
                    </Small>
                    <Medium>
                        <DescriptionBox
                            content={dataset.description}
                            truncateLength={500}
                        />
                    </Medium>
                </div>
                <div className="quality-rating-box">
                    <span>
                        <Link to="/page/dataset-quality">
                            Open Data Quality
                        </Link>:
                    </span>
                    &nbsp;&nbsp;
                    <StarRating stars={dataset.linkedDataRating} />
                </div>
                <TagsBox tags={dataset.tags} />
                <div className="dataset-preview">
                    <DatasetPreview dataset={dataset} />
                </div>

                <div className="row">
                    <div className="col-sm-12">
                        <div className="dataset-details-files-apis">
                            <h2 className="clearfix">
                                <span className="section-heading">
                                    Files and APIs
                                </span>
                            </h2>
                            <div className="clearfix">
                                {dataset.distributions.map(s => (
                                    <DistributionRow
                                        key={s.identifier}
                                        distribution={s}
                                        datasetId={datasetId}
                                        searchText={searchText}
                                    />
                                ))}
                            </div>
                        </div>
                        <div className="dataset-details-source">
                            <h2 className="section-heading">Data Source</h2>
                            <MarkdownViewer
                                markdown={source}
                                truncate={false}
                            />
                            <a
                                className="landing-page"
                                href={dataset.landingPage}
                            >
                                {dataset.landingPage}
                            </a>
                        </div>
                        <div
                            className="dataset-details-temporal-coverage"
                            style={{ display: "none" }}
                        >
                            <h2 className="section-heading">
                                Temporal coverage
                            </h2>
                            <TemporalAspectViewer
                                data={dataset.temporalCoverage}
                            />
                        </div>
                    </div>
                </div>
            </div>
        );
    }
}

function mapStateToProps(state) {
    const record = state.record;
    const dataset = record.dataset;
    return {
        dataset
    };
}

export default connect(mapStateToProps)(DatasetDetails);
