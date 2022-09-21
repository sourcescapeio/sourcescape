export function Test() {
  return <div></div>
}

// import React from 'react';

// import {
//   MultilineHighlight,
// } from './CompletionHighlight';

// import { AddArgHighlightSpan } from './AddArgHighlightSpan';
// import { ArgHighlightSpan } from './ArgHighlightSpan';
// import { BaseHighlightSpan } from './BaseHighlightSpan';
// import { BodyHighlightSpan } from './BodyHighlightSpan';
// import { NameHighlightSpan } from './NameHighlightSpan';
// import { IndexOnlyHighlightSpan } from './IndexOnlyHighlightSpan';

// import { wrapChildren } from './layout';

// // types
// const BASE = "base";
// const BODY = "body";
// const NAME = "name";
// const ARG = "arg";
// const EMPTY_ARG = "empty-arg";
// const EMPTY = "empty";
// const INDEX_ONLY = "index-only";
// const CAN_HIGHLIGHT = [BASE, NAME, ARG, EMPTY];


// class HighlightSpan extends React.Component {
//   shouldComponentUpdate(nextProps) {
//     // highlight never changes
//     // component is fully remounted whenever server postOperation
//     const { nodes } = this.props;
//     const highlight = nodes[0];

//     // check for trace or debug change
//     if (nextProps.trace !== this.props.trace || nextProps.debug !== this.props.debug) {
//       return true;
//     }
//     const hoveredDelta = (this.props.hovered === highlight.id) !== (nextProps.hovered === highlight.id);
//     const selectedDelta = (this.props.selected.includes(highlight.id) !== nextProps.selected.includes(highlight.id));
//     const referencedDelta = (this.props.references.includes(highlight.id) !== nextProps.references.includes(highlight.id));

//     if (hoveredDelta || selectedDelta || referencedDelta) {
//       // also some unneeded updates for suppressed stuff, but let's keep the logic simple
//       return CAN_HIGHLIGHT.includes(highlight.type);
//     }

//     if (highlight.type === BODY) {
//       const currentBody = (this.props.bodyId === highlight.bodyId);
//       const nextBody = (nextProps.bodyId === highlight.bodyId);

//       // change in body selection
//       if (currentBody !== nextBody) {
//         return true;
//       }

//       if (nextBody) {
//         // if it's selected, we'll always update
//         // slightly inefficient, but greatly simplifies logic
//         return true;
//       }

//       return false;
//     } else {
//       return false;
//     }
//   }

//   render() {
//     const props = this.props;
//     const { nodes } = props;
//     const { deleteItem, deleteEdge, setName, unsetName, setAlias, setIndex, unsetIndex, addArg, select, deselect } = props;
//     const { isDragging, debug, trace } = props;

//     const highlight = nodes[0];
//     // console.warn('REDRAW', highlight);
    
//     // wrap children
//     const hovered = props.hovered === highlight.id;
//     const selected = props.selected.includes(highlight.id);
//     const referenced = props.references.includes(highlight.id);

//     const { children } = props;

//     if (highlight.type === BASE) {
//       return <BaseHighlightSpan
//         id={highlight.id}
//         highlight={highlight}
//         multilineHighlight={highlight.multilineHighlight}
//         suppressHighlight={highlight.suppressHighlight}
//         edgeFrom={highlight.edgeFrom}        
//         //
//         referenced={referenced}      
//         selected={selected}
//         hovered={hovered}
//         // actions
//         appendCurrentBody={props.appendCurrentBody}
//         suppressClear={props.suppressClear}
//         unsuppressClear={props.unsuppressClear}
//         select={select}
//         deselect={deselect}
//         setHovered={props.setHovered}
//         setBody={props.setBody}
//         delete={deleteItem}
//         deleteEdge={deleteEdge}
//         //
//         trace={trace}
//       >
//         {wrapChildren(highlight.color, children)}
//       </BaseHighlightSpan>
//     } else if (highlight.type === BODY) {
//       return <BodyHighlightSpan
//         id={highlight.bodyId}
//         highlight={highlight}
//         // 
//         bodyId={props.bodyId}
//         setBody={props.setBody}
//         clearBody={props.clearBody}
//         //
//         editorState={props.editorState}
//         blockText={props.blockText}
//         onChange={props.onChange}
//         //
//         suggestions={props.suggestions}
//         selectedIndex={props.selectedIndex}
//         // actions
//         suggestionUp={props.suggestionUp}
//         suggestionDown={props.suggestionDown}
//         suggestionEnter={props.suggestionEnter}
//         suggestionFill={props.suggestionFill}
//       >
//         {children}
//       </BodyHighlightSpan>
//     } else if (highlight.type === NAME) {
//       return <NameHighlightSpan
//         id={highlight.id}
//         highlight={highlight}
//         multilineHighlight={highlight.multilineHighlight}
//         suppressHighlight={highlight.suppressHighlight}
//         color={highlight.color}
//         //
//         appendCurrentBody={props.appendCurrentBody}
//         suppressClear={props.suppressClear}
//         unsuppressClear={props.unsuppressClear}        
//         setName={setName}
//         unsetName={unsetName}
//         delete={deleteItem}
//         setBody={props.setBody}
//         //
//         referenced={referenced}      
//         hovered={hovered}
//         selected={selected}
//         select={select}
//         deselect={deselect}
//         setHovered={props.setHovered}
//         //
//         trace={trace}
//       >
//         {children}
//       </NameHighlightSpan>    
//     } else if (highlight.type === INDEX_ONLY) {
//       return <IndexOnlyHighlightSpan
//         id={highlight.id}
//         parentId={highlight.parentId}
//         index={highlight.index}
//         // actions
//         setIndex={setIndex}
//         unsetIndex={unsetIndex}        
//         setHovered={props.setHovered}
//       >
//         {children}
//       </IndexOnlyHighlightSpan>
//     } else if (highlight.type === ARG) {
//       return <ArgHighlightSpan
//         id={highlight.id}
//         highlight={highlight}
//         setAlias={setAlias}
//         delete={deleteItem}
//         // for drag drop
//         parentId={highlight.parentId}
//         index={highlight.index}
//         isDragging={isDragging}
//         //
//         appendCurrentBody={props.appendCurrentBody}
//         suppressClear={props.suppressClear}
//         unsuppressClear={props.unsuppressClear}        
//         setBody={props.setBody}
//         //
//         hovered={hovered}
//         selected={selected}
//         setHovered={props.setHovered}      
//         select={select}
//         deselect={deselect}
//         referenced={referenced}
//         //
//         trace={trace}
//       >
//         {children}
//       </ArgHighlightSpan>
//     } else if (highlight.type === EMPTY_ARG) {
//       return <AddArgHighlightSpan
//         id={highlight.id}
//         parent={highlight.parent}
//         index={highlight.index}
//         addArg={addArg}
//         trace={trace}
//       >
//         {children}
//       </AddArgHighlightSpan>
//     } else if (highlight.type === EMPTY) {
//       return <MultilineHighlight referenced={referenced} selected={selected} hovered={hovered}>
//         {highlight.multilineHighlight}
//       </MultilineHighlight>
//     } else {
//       return <span>{children}</span>;
//     }
//   }
// }

// export {
//   HighlightSpan,
// }
