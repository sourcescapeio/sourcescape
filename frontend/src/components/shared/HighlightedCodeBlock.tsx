import React from 'react';

import { 
  EditorState, 
  Editor, 
  convertFromRaw,
  CompositeDecorator,
  SelectionState,
  Modifier,
} from 'draft-js';

import Draft from 'draft-js';

import { refractor } from 'refractor';

import styled from 'styled-components';
import Col from 'react-bootstrap/Col';
import Container from 'react-bootstrap/Container';

import _ from 'lodash';

const PrismDecorator = require('draft-js-prism');
const MultiDecorator= require('draft-js-multidecorators');

const EditorContainer = styled.div`
display: flex;
overflow: scroll;

.public-DraftEditor-content {

  div.public-DraftStyleDefault-block {
    overflow: scroll;
    white-space: pre;
  }

  span.token {
    display:inline-block;
  }
}
`;

function LineNumbers(props: { lineCount: number, startingLineNumber: number }) {
  const { lineCount } = props;
  const lineArray = Array.from(Array(lineCount).keys())
  if (lineCount > 0) {
    const lineNumbers = lineArray.map((idx) => {
      return <li key={idx}>{idx + props.startingLineNumber}</li>
    });
    //maxWidth: 100, 
    return <div style={{textAlign: 'center', backgroundColor: 'rgb(18, 26, 41)'}}>
      <ul style={{
        textAlign: 'right',
        paddingLeft: 10,
        paddingRight: 10,
        fontFamily: "'Source Code Pro', monospace",
        color: 'rgb(86, 99, 122)',
        listStyle: 'none'}}
      >
        {lineNumbers}
      </ul>
    </div>;
  } else {
    return <div></div>
  }
}


const calculateNodeOffsets = (nodes: any[] | null): NodeOffset[] => {
  const nodeOffsetsBase = (nodes || []).map((n) => {

    const { start_index, end_index } = n;

    return {
      node: n, 
      start: start_index,
      end: end_index,
    };
  });

  const startGrouped = _.groupBy(nodeOffsetsBase, (no) => (no.start));
  const nodeOffsets = _.values(startGrouped).map((items, i) => {
    const maxEnd = Math.max(...items.map((i) => (i.end)));
    const start: number = items[0].start;

    return {
      nodes: items.map((i) => (i.node)),
      start,
      end: maxEnd,
    }
  });
  const nodeOffsetsDeduped = nodeOffsets.reduce<{ignore: any, acc: NodeOffset[]}>((
    prev: {ignore: any, acc: NodeOffset[]},
    i: NodeOffset,
    idx: number
  ) => {
    const { ignore, acc } = prev;
    const { start, end, nodes } = i;
    if (ignore.has(start)) {
      return {
        ignore,
        acc,
      };
    }
    
    const dupes = nodeOffsets.slice(idx + 1).filter((no) => (no.end <= end));
    const dupeNodes = _.flatMap(dupes, (i) => (i.nodes));
    dupes.forEach((d) => {
      ignore.add(d.start);
    });

    const newNodes = [
      ...nodes,
      ...dupeNodes,
    ];

    return {
      ignore,
      acc: [
        ...acc,
        {
          nodes: newNodes,
          start,
          end,
        }
      ],
    };
  }, {
    ignore: new Set(), // mutable for speed
    acc: [],
  });

  return nodeOffsetsDeduped.acc;
};

const calculateEdgeIndex = (edges: {from: string, to: string}[]) => {
  return {
    fromEdges: _.groupBy(edges, (e) => (e.from)),
    toEdges: _.groupBy(edges, (e) => (e.to)),
  };
}

const generateContentState = (text: string, nodeOffsets: any[]) => {
  const baseContentState = convertFromRaw({
    entityMap: {},
    blocks: [
      {
        type: 'code-block',
        text: text,
        key: '', // TODO: fix
        depth: 0,
        inlineStyleRanges: [],
        entityRanges: []
      }
    ]
  });

  const contentState = nodeOffsets.reduce((acc, { nodes, start, end }) => {
    const n = acc.createEntity(
      'NODE',
      'IMMUTABLE',
      {
        id: nodes[0].id,
        nodes,
      }
    );
    const entityKey = n.getLastCreatedEntityKey();
    const block = n.getFirstBlock();
    const blockId = block.getKey();
    const selection = SelectionState.createEmpty(blockId).merge({
      anchorOffset: start, 
      focusOffset: end
    });
    const nextState = Modifier.applyEntity(
      n,
      selection,
      entityKey
    );
    return nextState;
  }, baseContentState);  

  return contentState;
}

const DecoratorHelper = (InnerComponent: React.ComponentType) => (props: any) => {
  const { contentState, start, end } = props;
  if (contentState) {
    const block = contentState.getFirstBlock();
    const entityKey = block.getEntityAt(start);
    if (entityKey) {
      const entity = contentState.getEntity(entityKey);
      const { data } = entity;

      return <InnerComponent {...props} id={data.id} nodes={data.nodes}/>
    }
  }

  // Indicate error
  return (
    <span 
      onClick={() => (alert(JSON.stringify({contentState, start, end})))} 
      style={{
        backgroundColor: 'rgb(155, 0, 0, 0.8)'
      }}
    >
      {props.children}
    </span>
  );
}


// nodes
// {
//   start_index,
//   end_index,
//   id,
//   type,
//   repo,
//   file,
// }

// edges
// { from:, to: }

// component interface
// {
//   nodes, << all nodes
//   id, << 
//   selectedNode,
//   fromEdges,
//   toEdges,
//   selectNode,
// }


type CodeBlockProps = {
  text: string,
  nodes: any[],
  edges: any[],
  //
  language: string,
  hideLineNumbers?: boolean,
  divStyle?: any,
  snippetStartLine: number,
  //
  componentProps?: any
};

type NodeOffset = {
  start: number,
  end: number,
  nodes: any[]
};

type CodeBlockState = {
  nodeOffsets: NodeOffset[],
  editorState: EditorState,
  lines: string[],
  selectedNode: null | string,
  edgeIndex: any
}

const HighlightedCodeBlockComponent = (component: React.ComponentType) => {

  const InnerComponent = DecoratorHelper(component);

  return class extends React.Component<CodeBlockProps, CodeBlockState> {
    state = {
      selectedNode: null,
      lines: [],
      editorState: EditorState.createEmpty(),
      nodeOffsets: [],
      edgeIndex: {}
    }

    componentDidMount() {
      if(this.props.text !== "") {
        this.initialize(this.props.text, this.props.nodes || [], this.props.edges || []);
      }
    }

    componentDidUpdate(prevProps: CodeBlockProps, prevState: CodeBlockState) {
      // this is so ugly
      const hadUpdate = !_.isEqual(prevProps.nodes, this.props.nodes) || !_.isEqual(prevProps.edges, this.props.edges) || !_.isEqual(prevProps.text, this.props.text);
      if (hadUpdate && this.props.text !== "") {
        this.initialize(this.props.text, this.props.nodes || [], this.props.edges);
        // TODO: FUCK THIS IS BAD
        // cannot do comparison on editor state
      } else if (this.state.selectedNode !== prevState.selectedNode || !_.isEqual(_.omit(prevProps.componentProps, 'editorState'), _.omit(this.props.componentProps, 'editorState'))) {
        this.updateEditorState();
      }
    }

    calculateDecorators = (nodeOffsets: { start: number, end: number}[], edgeIndex: any) => {
      // console.warn('RECALCULATE');
      const analysisStrategy = (
        contentBlock: Draft.ContentBlock, 
        callback: (_: number, __: number) => void, 
        contentState: Draft.ContentState
      ) => {
        // go through nodeOffsetMap
        nodeOffsets.map((no) => {
          callback(no.start, no.end);
        });
      };

      const { selectedNode } = this.state;
      const { componentProps } = this.props;
      // const { fromEdges, toEdges } = edgeIndex;

      const compositeDecorator = new CompositeDecorator([
        {
          strategy: analysisStrategy,
          component: InnerComponent,
          props: {
            selectedNode: selectedNode,
            selectNode: (n: any) => {
              this.setState({
                selectedNode: n
              });
            },
            ...(componentProps || {})
          }
        }
      ]);

      const decorator = new MultiDecorator([
        new PrismDecorator({
          // Provide your own instance of PrismJS
          prism: refractor,
          defaultSyntax: this.props.language,
        }),
        compositeDecorator,
      ]);

      return decorator;
    };

    updateEditorState = () => {
      const decorator = this.calculateDecorators(
        this.state.nodeOffsets,
        this.state.edgeIndex,
      );

      const editorState = EditorState.set(this.state.editorState, { decorator });

      this.setState({
        editorState,
      });
    }

    initialize = (text: string, nodes: any[], edges: any[]) => {
      const lines = text.replace(/\n$/, '').split('\n');

      const nodeOffsets = calculateNodeOffsets(nodes);
      const edgeIndex = calculateEdgeIndex(edges);
      const contentState = generateContentState(text, nodeOffsets);

      /**
        * decorators
        */
      const decorator = this.calculateDecorators(nodeOffsets, edgeIndex);

      const editorState = EditorState.createWithContent(contentState, decorator);

      this.setState({
        lines,
        nodeOffsets,
        edgeIndex,
        editorState,
      });
    }

    render() {
      const { snippetStartLine, hideLineNumbers, divStyle } = this.props;
      const { lines, editorState } = this.state;

      return <div>
        <EditorContainer>
          {
            !hideLineNumbers ? <LineNumbers 
              startingLineNumber={snippetStartLine || 1}
              lineCount={lines.length}
            /> : null
          }
          <Col xs={12} style={{
            backgroundColor: 'rgb(40, 52, 71)',
            paddingLeft: 10
          }}>
            <Container style={{
              maxWidth: '90vw', 
              padding: 0,
              overflowX: 'scroll'
            }}>
              <Editor 
                // className="editor"
                readOnly={true}
                editorState={editorState}
                onChange={() => (null)}
              />
            </Container>
          </Col>
        </EditorContainer>
      </div>
    }
  }
}

export {
  HighlightedCodeBlockComponent,
};
