import "./Tooltip.css";
import { Link } from "react-router-dom";
import React from "react";
import helpIcon from "../assets/help-24.svg";

/**
 * @description Return a information tooltip, on hover show calculation method.
 * @returns { div }
 */
const Tooltip = () => {
    return (
        <div className="tooltip">
            <img src={helpIcon} alt="Help Link" />
            <span className="tooltiptext">
                Calculated using the{" "}
                <Link to="/page/dataset-quality">Open data quality</Link>
            </span>
        </div>
    );
};

export default Tooltip;
